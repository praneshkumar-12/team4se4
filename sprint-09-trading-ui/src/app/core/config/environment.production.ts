/**
 * Production config. No secrets, no API keys, no Fauxnance host — ever
 * (SEC4-642 greps the built bundle for exactly that).
 *
 * Unlike dev, these are real absolute origins: production serves this SPA
 * from its own host and calls the platform APIs on theirs, so the
 * SEC4-636 interceptor's origin allow-list is doing real work here, not
 * collapsed into one origin by a dev proxy. Fill in the real hosts before
 * shipping — placeholders below are deliberately obvious placeholders,
 * not values anyone could mistake for live secrets.
 */
export const environment = {
  production: true,
  apiBaseUrls: {
    auth: 'https://auth.REPLACE_WITH_REAL_HOST.example',
    trade: 'https://trade.REPLACE_WITH_REAL_HOST.example',
  },
  allowedOrigins: [
    'https://auth.REPLACE_WITH_REAL_HOST.example',
    'https://trade.REPLACE_WITH_REAL_HOST.example',
  ],
};
