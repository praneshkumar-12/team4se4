import type { Page } from '@playwright/test';

import { E2E_AUTH_EMAIL, E2E_AUTH_PASSWORD } from './env';

/**
 * Signs in through the real UI, for journeys that need to start
 * authenticated. The bearer token (`TokenStore`) is intentionally
 * in-memory only, so any hard navigation after this (a `page.goto`, a
 * reload) signs the user straight back out - never follow this with
 * `page.goto` to reach another protected route. Use `signInAndGoTo`
 * instead, which lands on that route through the app's own client-side
 * guard-redirect, the same way a real signed-out user would.
 */
export async function signIn(page: Page): Promise<void> {
  await page.goto('/sign-in');
  await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
  await page.getByTestId('sign-in-password').fill(E2E_AUTH_PASSWORD);
  await page.getByTestId('sign-in-submit').click();
  await page.waitForURL(/\/blotter/);
}

/**
 * Signs in and ends up on `path`, via the guard redirect (`/sign-in` with
 * `?returnUrl=`) rather than a hard navigation to `path` after signing in -
 * the latter would reload the app and drop the in-memory token before the
 * route guard ever got to check it.
 */
export async function signInAndGoTo(page: Page, path: string): Promise<void> {
  await page.goto(`/sign-in?returnUrl=${encodeURIComponent(path)}`);
  await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
  await page.getByTestId('sign-in-password').fill(E2E_AUTH_PASSWORD);
  await page.getByTestId('sign-in-submit').click();
  await page.waitForURL(new RegExp(path.replace(/[/]/g, '\\/')));
}
