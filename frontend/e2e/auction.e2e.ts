import { expect, test } from '@playwright/test';
import { E2E_BUYER, E2E_USER } from './global-setup';
import { login } from './support';

const ITEM = 'Buty Włóczykija';

/**
 * Seller puts an item up for auction; a buyer in another session bids on it.
 * The buyer leads, and the seller's open auction page shows the new highest bid with its author.
 */
test('buyer outbids the starting price and the seller sees the bid live', async ({ browser }) => {
  const sellerContext = await browser.newContext();
  const buyerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const buyer = await buyerContext.newPage();

  await login(seller, E2E_USER);
  await seller.goto('/new?mode=auction');
  await expect(seller.getByRole('heading', { name: 'Wystaw przedmiot na licytację' })).toBeVisible();
  await seller.getByPlaceholder('Zacznij wpisywać nazwę przedmiotu').fill(ITEM);
  await seller.locator('button.catalog-item', { hasText: ITEM }).first().click();
  await seller.getByLabel('Waluta (zł/złoto)').selectOption({ label: 'w grze' });
  await seller.getByLabel('Serwer').selectOption({ index: 1 });
  await seller.locator('input[formcontrolname="price"]').fill('2g');
  await seller.getByRole('button', { name: 'Wystaw na licytację' }).click();

  await expect(seller).toHaveURL(/\/auctions\/\d+$/);
  const auctionUrl = new URL(seller.url()).pathname;
  const sellerPanel = seller.getByRole('complementary', { name: 'Stan licytacji' });
  await expect(sellerPanel).toContainText('Cena wywoławcza');

  await login(buyer, E2E_BUYER);
  await buyer.goto(auctionUrl);
  await buyer.getByRole('textbox', { name: /Twoja oferta/ }).fill('3g');
  await buyer.getByRole('button', { name: 'Złóż ofertę' }).click();
  await expect(buyer.getByText('Oferta została przyjęta.')).toBeVisible();
  await expect(buyer.getByText('Twoja oferta prowadzi.')).toBeVisible();

  // The seller did not reload: the bid arrives over the auction topic.
  await expect(sellerPanel).toContainText('Najwyższa ważna oferta');
  await expect(seller.locator('table.history tbody tr').first()).toContainText(E2E_BUYER.displayName);

  await sellerContext.close();
  await buyerContext.close();
});
