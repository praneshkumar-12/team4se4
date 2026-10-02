import { expect, test } from '@playwright/test';

import { signInAndGoTo } from './helpers';

/**
 * SEC4-641 — place-order journey. `TCS` is one of `sprint-06-trade-api`'s
 * demo-seeded, active, tradable instruments (see
 * `003-demo-seed.sql`) and the signed-in demo account (ACC-10001) has
 * ample cash for one unit of it — fixed test data, not a credential, so it
 * stays a literal here rather than in `.env.example`.
 *
 * Each test signs in for itself (via the real UI, through `signIn`) and
 * places nothing another test depends on, so these stand on their own.
 */
const SYMBOL = 'TCS';
const PRICE = '10.00';

test.describe('place order', () => {
  test.beforeEach(async ({ page }) => {
    await signInAndGoTo(page, '/orders/new');
  });

  test('the account field is read-only', async ({ page }) => {
    const accountField = page.getByTestId('account-id');
    await expect(accountField).toBeVisible();
    await expect(accountField).toHaveText(/^\d+$/);

    const tagName = await accountField.evaluate((element) => element.tagName);
    expect(['INPUT', 'TEXTAREA', 'SELECT']).not.toContain(tagName);

    const isContentEditable = await accountField.evaluate((element) => (element as HTMLElement).isContentEditable);
    expect(isContentEditable).toBe(false);

    // There is no "account" form control at all - the accountId the
    // submit handler sends always comes from the signed-in token, never
    // from a value typed into the form, so there is nothing to submit a
    // different account through.
    await expect(page.locator('[formcontrolname="account"], [formcontrolname="accountId"]')).toHaveCount(0);
  });

  test('an invalid quantity is rejected before it ever reaches the wire', async ({ page }) => {
    await page.getByLabel('Symbol').fill(SYMBOL);
    await page.getByRole('combobox', { name: 'Select side' }).click();
    await page.getByRole('option', { name: 'Buy' }).click();
    await page.getByLabel('Quantity').fill('0');
    await page.getByLabel('Price').fill(PRICE);

    await page.getByRole('button', { name: 'Submit order' }).click();

    await expect(page.getByText('Enter a whole quantity above zero.')).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page).toHaveURL(/\/orders\/new/);
  });

  test('a valid order is placed and shows the status that came back', async ({ page }) => {
    await page.getByLabel('Symbol').fill(SYMBOL);
    await page.getByRole('combobox', { name: 'Select side' }).click();
    await page.getByRole('option', { name: 'Buy' }).click();
    await page.getByLabel('Quantity').fill('1');
    await page.getByLabel('Price').fill(PRICE);

    await page.getByRole('button', { name: 'Submit order' }).click();

    // Execution has been asynchronous since Sprint 7 - this can only hold
    // the UI to "one of the three valid statuses, actually shown"
    // (SEC4-641's own note), never a specific one.
    const outcome = page.getByRole('alert');
    await expect(outcome).toBeVisible();
    await expect(outcome).toContainText(/is (new|filled|rejected)/i);
  });
});
