import { expect, test } from '@playwright/test';
import { E2E_USER, sql } from './global-setup';

/**
 * The main seller path through the real UI, backend and database:
 * log in → list an item priced in gold (6.5g typed as 6500m) → see it in "Moje ogłoszenia"
 * → mark it as sold → the page confirms it and the database agrees.
 */
test('seller lists an item for 6,5g and marks it as sold', async ({ page }) => {
  await page.goto('/auth');
  await page.getByLabel('Login').fill(E2E_USER.username);
  await page.getByLabel('Hasło', { exact: true }).fill(E2E_USER.password);
  await page.getByRole('button', { name: 'Zaloguj' }).click();
  await expect(page.getByRole('link', { name: 'Moje ogłoszenia' })).toBeVisible();

  await page.goto('/new');
  await page.getByPlaceholder('Zacznij wpisywać nazwę przedmiotu').fill('Lisia ochrona');
  await page.locator('button.catalog-item', { hasText: 'Lisia ochrona' }).first().click();
  await expect(page.locator('.selected-item')).toContainText('Lisia ochrona');

  await page.getByLabel('Waluta (zł/złoto)').selectOption({ label: 'w grze' });
  await page.getByLabel('Serwer').selectOption({ index: 1 });
  const price = page.locator('input[formcontrolname="price"]');
  await price.fill('6500m');
  await price.blur();
  // The form rewrites any valid gold price to one form.
  await expect(price).toHaveValue('6,5g');

  await page.getByRole('button', { name: 'Dodaj ogłoszenie' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  const card = page.locator('mm-listing-card', { hasText: 'Lisia ochrona' });
  await expect(card).toBeVisible();
  await expect(card).toContainText('6,5g');

  await card.getByRole('button', { name: 'Oznacz jako sprzedane' }).click();
  await expect(page.locator('.notice')).toHaveText('Lisia ochrona oznaczono jako sprzedane.');
  await expect(page.locator('.notice')).not.toContainText('Nie udało się');

  const row = sql(`SELECT l.price || '|' || s.name FROM listings l JOIN listing_statuses s ON s.id = l.status_id
                   JOIN users u ON u.id = l.user_id WHERE u.username = '${E2E_USER.username}'`).trim();
  expect(row).toBe('6500000000|sold');
});
