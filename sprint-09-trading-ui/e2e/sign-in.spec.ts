import { expect, test } from '@playwright/test';

import { E2E_AUTH_EMAIL, E2E_AUTH_INVALID_PASSWORD, E2E_AUTH_PASSWORD } from './env';

/**
 * SEC4-641 — sign-in journey. Every test starts from a fresh, signed-out
 * browser context (Playwright gives each test its own context by default)
 * and signs in for itself; nothing here depends on another test's order or
 * leftover state.
 */
test.describe('sign-in', () => {
  test('visiting a protected route while signed out redirects to sign-in', async ({ page }) => {
    await page.goto('/orders/new');

    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Forders%2Fnew/);
    await expect(page.getByTestId('sign-in-email')).toBeVisible();
  });

  test('a refused sign-in shows a readable error', async ({ page }) => {
    await page.goto('/sign-in');

    await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
    await page.getByTestId('sign-in-password').fill(E2E_AUTH_INVALID_PASSWORD);
    await page.getByTestId('sign-in-submit').click();

    await expect(page.getByRole('alert')).toContainText('Invalid email or password.');
    await expect(page).toHaveURL(/\/sign-in/);
  });

  test('a successful sign-in lands on the blotter', async ({ page }) => {
    await page.goto('/sign-in');

    await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
    await page.getByTestId('sign-in-password').fill(E2E_AUTH_PASSWORD);
    await page.getByTestId('sign-in-submit').click();

    await expect(page).toHaveURL(/\/blotter/);
    await expect(page.getByTestId('sign-out')).toBeVisible();
  });

  test('signing in from a guard redirect returns to the original route', async ({ page }) => {
    await page.goto('/orders/new');
    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Forders%2Fnew/);

    await page.getByTestId('sign-in-email').fill(E2E_AUTH_EMAIL);
    await page.getByTestId('sign-in-password').fill(E2E_AUTH_PASSWORD);
    await page.getByTestId('sign-in-submit').click();

    await expect(page).toHaveURL(/\/orders\/new/);
    await expect(page.getByTestId('account-id')).toBeVisible();
  });
});
