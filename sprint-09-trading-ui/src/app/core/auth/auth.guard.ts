import { inject } from '@angular/core';
import type { CanActivateFn } from '@angular/router';
import { Router } from '@angular/router';

import { ROUTE_PATHS } from '../config/routes.const';
import { TokenStore } from './token-store';

/**
 * Applied to every route except `ROUTE_PATHS.signIn` (see `app.routes.ts`).
 * Returning a `UrlTree` rather than calling `router.navigate` lets the
 * router redirect as part of resolving this guard, so an unauthenticated
 * visitor never sees a blank route flash before the redirect happens.
 *
 * This is a usability control, not a security control — the bundle is
 * public and the Trade REST API is the one that actually authorises every
 * call. Don't extend this to do more than "am I signed in."
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);

  if (tokenStore.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree([ROUTE_PATHS.signIn], {
    queryParams: { returnUrl: state.url },
  });
};
