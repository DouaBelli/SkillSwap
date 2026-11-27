import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { REQUIRE_LOGIN_IN_UI } from './api';
import { Session } from './session';

/** Pages that need a login. Not logged in = sent to the Login screen. */
export const authGuard: CanActivateFn = () => {
  if (!REQUIRE_LOGIN_IN_UI || inject(Session).isLoggedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};
