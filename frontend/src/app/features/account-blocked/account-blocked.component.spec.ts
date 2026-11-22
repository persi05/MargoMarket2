import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AccountAccessService } from '../../core/services/account-access.service';
import { AuthService } from '../../core/services/auth.service';
import { AccountBlockedComponent } from './account-blocked.component';

describe('blocked account screen', () => {
  afterEach(() => TestBed.resetTestingModule());
  it('shows a permanent block and restores the application after revocation', () => {
    TestBed.configureTestingModule({
      imports: [AccountBlockedComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isLoggedIn: true, logout: vi.fn() } },
      ],
    });
    const state = TestBed.inject(AccountAccessService);
    state.show({ reason: 'Powód blokady', expiresAt: null });
    const fixture = TestBed.createComponent(AccountBlockedComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Twoje konto jest zablokowane');
    expect(fixture.nativeElement.textContent).toContain('bezterminowo');
    (fixture.componentInstance as any).check();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/moderation')
      .flush({ accountBlock: null });
    expect(state.block()).toBeNull();
    fixture.destroy();
  });
});
