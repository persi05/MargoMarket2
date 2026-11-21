import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/services/auth.service';
import { AuthPageComponent } from './auth-page.component';

describe('auth page rate limiting', () => {
  let login: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    login = vi.fn();
    TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login, register: vi.fn(), verifyEmail: vi.fn() } }]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  function signIn(status: number, error: unknown) {
    login.mockReturnValue(throwError(() => new HttpErrorResponse({ status, error })));
    const fixture = TestBed.createComponent(AuthPageComponent);
    fixture.detectChanges();
    const page = fixture.componentInstance as any;
    page.form.patchValue({ username: 'gracz', password: 'haslo' });
    page.submit();
    fixture.detectChanges();
    return fixture;
  }

  const submitButton = (fixture: ReturnType<typeof signIn>) =>
    fixture.nativeElement.querySelector('button.submit') as HTMLButtonElement;

  it('counts down and blocks the form after too many login attempts', () => {
    const fixture = signIn(429, { code: 'RATE_LIMITED', retryAfterSeconds: 3 });
    expect(fixture.nativeElement.textContent).toContain('Zbyt wiele prób. Spróbuj ponownie za 3 s.');
    expect(submitButton(fixture).disabled).toBe(true);

    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Spróbuj ponownie za 2 s.');

    (fixture.componentInstance as any).submit();
    expect(login).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(2000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Zbyt wiele prób');
    expect(submitButton(fixture).disabled).toBe(false);
    fixture.destroy();
  });

  it('keeps the regular message for wrong credentials', () => {
    const fixture = signIn(401, null);
    expect(fixture.nativeElement.textContent).toContain('Nie udało się zalogować. Sprawdź login i hasło.');
    expect(submitButton(fixture).disabled).toBe(false);
    fixture.destroy();
  });
});
