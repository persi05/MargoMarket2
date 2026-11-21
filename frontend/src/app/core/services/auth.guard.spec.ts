import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

function run(loggedIn: boolean) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { isLoggedIn: loggedIn } }] });
  return TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, { url: '/messages/7' } as RouterStateSnapshot));
}

describe('auth guard', () => {
  it('lets a logged in user through', () => {
    expect(run(true)).toBe(true);
  });

  it('sends a guest to the login page and remembers where they were going', () => {
    const result = run(false) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/auth?returnUrl=%2Fmessages%2F7');
  });
});
