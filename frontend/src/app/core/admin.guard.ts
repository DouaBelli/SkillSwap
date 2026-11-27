import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from './session';

export const adminGuard: CanActivateFn = () => {
  const session = inject(Session);
  if (!session.isLoggedIn()) return inject(Router).createUrlTree(['/login']);
  return session.isAdmin() ? true : inject(Router).createUrlTree(['/']);
};