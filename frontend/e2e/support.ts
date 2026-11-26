import { expect, Page } from '@playwright/test';
import { E2eUser } from './global-setup';

/** Logs in through the login form and waits until the session is visible in the navigation. */
export async function login(page: Page, user: E2eUser): Promise<void> {
  await page.goto('/auth');
  await page.getByLabel('Login').fill(user.username);
  await page.getByLabel('Hasło', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Zaloguj' }).click();
  await expect(page.getByRole('link', { name: 'Moje ogłoszenia' })).toBeVisible();
}

/** Lists a catalog item priced in gold through the listing form; ends on "Moje ogłoszenia". */
export async function listItem(page: Page, itemName: string, price: string): Promise<void> {
  await page.goto('/new');
  await page.getByPlaceholder('Zacznij wpisywać nazwę przedmiotu').fill(itemName);
  await page.locator('button.catalog-item', { hasText: itemName }).first().click();
  await expect(page.locator('.selected-item')).toContainText(itemName);
  await page.getByLabel('Waluta (zł/złoto)').selectOption({ label: 'w grze' });
  await page.getByLabel('Serwer').selectOption({ index: 1 });
  const priceInput = page.locator('input[formcontrolname="price"]');
  await priceInput.fill(price);
  await priceInput.blur();
  await page.getByRole('button', { name: 'Dodaj ogłoszenie' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('mm-listing-card', { hasText: itemName })).toBeVisible();
}

/**
 * Finds the seller's listing with the market search and sends the first message to the seller.
 * Ends on the new conversation, whose id is returned.
 */
export async function startConversation(page: Page, itemName: string, sellerName: string, message: string): Promise<number> {
  await page.goto('/items');
  await page.getByPlaceholder('Nazwa przedmiotu').fill(itemName);
  await page.getByRole('button', { name: 'Filtruj' }).click();
  const row = page.locator('tbody tr', { hasText: sellerName }).filter({ hasText: itemName });
  await expect(row).toHaveCount(1);
  await row.getByRole('link', { name: `Napisz do sprzedającego: ${itemName}` }).click();
  await expect(page).toHaveURL(/\/messages\/new\/\d+$/);

  await page.locator('#new-message').fill(message);
  await page.getByRole('button', { name: 'Wyślij wiadomość' }).click();
  await expect(page).toHaveURL(/\/messages\/\d+$/);
  await expect(page.locator('.chat-message.mine')).toContainText([message]);
  return Number(page.url().split('/').pop());
}
