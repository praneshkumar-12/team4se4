import type { Page } from '@playwright/test';

import { E2E_AUTH_EMAIL, E2E_AUTH_PASSWORD } from './env';

/** Signs in through the real UI, for journeys that need to start authenticated. */
export async function signIn(page: Page): Promise<void> {
  await page.goto('/sign-in');
  await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
  await page.getByTestId('sign-in-password').fill(E2E_AUTH_PASSWORD);
  await page.getByTestId('sign-in-submit').click();
  await page.waitForURL(/\/blotter/);
}
