import { expect, test } from '@playwright/test';
import { E2E_BUYER, E2E_USER } from './global-setup';
import { listItem, login, startConversation } from './support';

const ITEM = 'Rycerska przyłbica';

/**
 * Buyer and seller in two separate browser sessions. The buyer finds the listing with the market
 * search and writes to the seller; both messages reach the other side over the WebSocket,
 * without reloading the page.
 */
test('buyer finds a listing and talks to the seller in real time', async ({ browser }) => {
  const sellerContext = await browser.newContext();
  const buyerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const buyer = await buyerContext.newPage();

  await login(seller, E2E_USER);
  await listItem(seller, ITEM, '7g');
  await seller.goto('/messages');
  await expect(seller.getByText('Nie masz jeszcze rozmów.', { exact: false })).toBeVisible();

  await login(buyer, E2E_BUYER);
  await startConversation(buyer, ITEM, E2E_USER.displayName, 'Dzień dobry, czy przedmiot jest dostępny?');

  // The new conversation and its message appear on the seller's open page.
  const conversation = seller.locator('a.conversation-link', { hasText: ITEM });
  await expect(conversation).toContainText('Dzień dobry, czy przedmiot jest dostępny?');
  await conversation.click();
  await expect(seller.locator('.chat-message')).toContainText(['Dzień dobry, czy przedmiot jest dostępny?']);

  await seller.locator('#reply-message').fill('Tak, zapraszam do wymiany.');
  await seller.getByRole('button', { name: 'Wyślij wiadomość' }).click();

  await expect(buyer.locator('.chat-message').last()).toContainText('Tak, zapraszam do wymiany.');
  await expect(buyer.locator('.chat-message').last()).not.toHaveClass(/mine/);

  await sellerContext.close();
  await buyerContext.close();
});
