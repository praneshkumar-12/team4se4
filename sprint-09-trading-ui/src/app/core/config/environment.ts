/**
 * Local development defaults. Swapped for `environment.production.ts` at
 * build time via `angular.json`'s `production` configuration
 * (`fileReplacements`) — never read `production` at runtime to branch on.
 *
 * `apiBaseUrls` are relative, same-origin paths on purpose: `proxy.conf.json`
 * forwards them to the real `auth-service` (:3000) and `trade-api` (:8085)
 * started by `run-local.sh`, so the browser never makes a cross-origin
 * request in dev — neither backend has CORS configured (see
 * `docs/prompts/person-1-prompt.md`), and this sidesteps that without
 * touching either service.
 */
export const environment = {
  production: false,
  apiBaseUrls: {
    auth: '/auth-api',
    trade: '/trade-api',
  },
  /**
   * Origins the bearer-token interceptor (SEC4-636, Person 3) is allowed to
   * attach the Authorization header to. Same-origin because of the dev
   * proxy above — `window.location.origin` covers both backends in dev.
   * The interceptor still does a real origin comparison; this list is just
   * short because the proxy collapses two origins into one in dev.
   */
  allowedOrigins: [typeof window !== 'undefined' ? window.location.origin : ''],
};
