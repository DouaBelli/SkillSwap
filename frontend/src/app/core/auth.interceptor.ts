import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_URL } from './api';
import { Session } from './session';

/**
 * Adds the login token to every call to our backend.
 * If the backend says 401 (not logged in any more), the user is logged out and sent to /login.
 * If Person 1 uses a different way to log in, this is the only file to change.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(Session);
  const router = inject(Router);

  const token = session.token();
  const authReq =
    token && req.url.startsWith(API_URL)
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authReq).pipe(
    catchError((err) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && session.isLoggedIn()) {
        session.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
