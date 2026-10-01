import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStore } from '../auth/token-store';
import { environment } from '../config/environment';

/** Auth routes that take no credentials, matched on the end of the path so the base path can change. */
const UNAUTHENTICATED_AUTH_ROUTE = /\/auth\/(register|login|refresh)$/;

function resolveUrl(url: string): URL | null {
  try {
    return new URL(url, window.location.origin);
  } catch {
    return null;
  }
}

/**
 * The only place in the application that sets an Authorization header.
 * The token is attached only when the request's origin is on the
 * `environment.allowedOrigins` allow list, so a new third-party host
 * receives nothing until someone adds it on purpose.
 */
export const bearerTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStore).token();
  const target = resolveUrl(req.url);

  const isPlatformOrigin = target !== null && environment.allowedOrigins.includes(target.origin);
  const needsToken = isPlatformOrigin && !UNAUTHENTICATED_AUTH_ROUTE.test(target.pathname);

  if (token === null || !needsToken) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
