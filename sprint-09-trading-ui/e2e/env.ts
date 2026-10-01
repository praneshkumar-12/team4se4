/**
 * Every address and credential the Playwright journeys use, read from the
 * environment under the names declared in `.env.example` — never a literal
 * in a spec file. Missing a value fails loudly and immediately, rather than
 * a spec silently falling back to a baked-in default.
 */
function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `Missing required environment variable ${name}. Copy .env.example to .env and fill in real values.`,
    );
  }
  return value;
}

export const E2E_AUTH_EMAIL = required('E2E_AUTH_EMAIL');
export const E2E_AUTH_PASSWORD = required('E2E_AUTH_PASSWORD');
export const E2E_AUTH_INVALID_PASSWORD = required('E2E_AUTH_INVALID_PASSWORD');
