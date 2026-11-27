import { HttpErrorResponse } from '@angular/common/http';

/** Backend address. CORS in SecurityConfig already allows http://localhost:4200. */
export const API_URL = 'http://localhost:8080/api';

/**
 * true  = pages that need a login send you to /login when you are not logged in.
 * false = lets you open every screen without logging in (only to look at the design).
 */
export const REQUIRE_LOGIN_IN_UI = true;

/** A category or a department, as returned by /api/categories and /api/departments. */
export interface Option {
  id: number;
  name: string;
}

/** Turns an error from the backend ({ "message": "..." }) into text for the screen. */
export function errorMessage(
  err: unknown,
  fallback = 'Something went wrong. Please try again.',
): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Cannot reach the server. Is the backend running?';
    }
    const message = err.error?.message;
    if (typeof message === 'string' && message) {
      return message;
    }
  }
  return fallback;
}
