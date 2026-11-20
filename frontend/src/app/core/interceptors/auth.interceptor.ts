import { AccountAccessService } from '../services/account-access.service';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const access = inject(AccountAccessService);
  const token = authService.token;
  const isAuthRequest = req.url.includes('/auth/login') || req.url.includes('/auth/register') || req.url.includes('/auth/verify-email');

  const authorizedRequest = token
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    : req;

  return next(authorizedRequest).pipe(
    catchError((error) => {
      if(error instanceof HttpErrorResponse && error.status === 403 && error.error?.code === 'ACCOUNT_BLOCKED') access.show(error.error);

      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthRequest) {
        authService.logout();
      }

      return throwError(() => error);
    })
  );
};
