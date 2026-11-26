import { expect, test } from '@playwright/test';
import { E2E_ADMIN, E2E_BUYER, E2E_USER, sql } from './global-setup';
import { listItem, login, startConversation } from './support';

const ITEM = 'Szczurzy ryjek';
const REVIEW = 'Szybka i uczciwa wymiana, polecam.';

/**
 * A full trade with three accounts in separate sessions:
 * the buyer asks for an intermediary → an admin joins and confirms the exchange → the listing is sold
 * → only now the buyer can review the seller → the review is public on the seller's profile.
 */
test('confirmed exchange lets the buyer review the seller', async ({ browser }) => {
  const sellerContext = await browser.newContext();
  const buyerContext = await browser.newContext();
  const adminContext = await browser.newContext();
  const guestContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const buyer = await buyerContext.newPage();
  const admin = await adminContext.newPage();
  const guest = await guestContext.newPage();

  await login(seller, E2E_USER);
  await listItem(seller, ITEM, '12g');

  await login(buyer, E2E_BUYER);
  // Before any confirmed exchange there is nothing to review.
  await buyer.goto('/reviews');
  await expect(buyer.getByText('Nie masz wymian czekających na opinię.')).toBeVisible();

  const conversationId = await startConversation(buyer, ITEM, E2E_USER.displayName, 'Biorę, poproszę o pośrednika.');
  await buyer.getByRole('button', { name: 'Poproś o pośrednika' }).click();
  await expect(buyer.getByText('Oczekiwanie na pośrednika')).toBeVisible();

  await login(admin, E2E_ADMIN);
  await admin.goto('/admin');
  await admin.getByRole('button', { name: 'Wymiany', exact: true }).click();
  const request = admin.locator('.admin-queue-card', { hasText: ITEM }).filter({ hasText: E2E_BUYER.displayName });
  await expect(request).toContainText('Prośba o pośrednika');
  await request.getByRole('button', { name: 'Dołącz jako pośrednik' }).click();
  await expect(admin.getByText('Dołączono do rozmowy jako pośrednik.')).toBeVisible();

  await request.getByRole('link', { name: 'Otwórz rozmowę' }).click();
  await expect(admin).toHaveURL(new RegExp(`/messages/${conversationId}$`));
  await admin.getByRole('button', { name: 'Potwierdź wykonaną wymianę' }).click();
  await expect(admin.locator('.system-message', { hasText: 'Administrator potwierdził wykonanie wymiany' })).toBeVisible();

  const listing = sql(`SELECT s.name FROM conversations c JOIN listings l ON l.id = c.listing_id
                       JOIN listing_statuses s ON s.id = l.status_id WHERE c.id = ${conversationId}`).trim();
  expect(listing).toBe('sold');

  // The buyer's conversation is closed and points to the review.
  await buyer.reload();
  await expect(buyer.getByText('Wymiana potwierdzona przez pośrednika')).toBeVisible();
  await buyer.getByRole('link', { name: 'Oceń sprzedawcę' }).click();
  await expect(buyer).toHaveURL(/\/reviews$/);

  const pending = buyer.locator('article.pending', { hasText: ITEM });
  await pending.getByRole('radio', { name: '5 / 5 – Bardzo dobrze' }).click();
  await pending.getByLabel('Komentarz (opcjonalnie)').fill(REVIEW);
  await pending.getByRole('button', { name: 'Opublikuj opinię' }).click();
  await expect(buyer.getByRole('status')).toHaveText(`Dziękujemy! Opinia o sprzedawcy ${E2E_USER.displayName} została opublikowana.`);
  await expect(buyer.getByText('Nie masz wymian czekających na opinię.')).toBeVisible();

  // Anyone, also without an account, sees the review on the seller's profile.
  const sellerId = sql(`SELECT id FROM users WHERE username = '${E2E_USER.username}'`).trim();
  await guest.goto(`/u/${sellerId}/profil`);
  await guest.getByRole('button', { name: 'Pokaż opinie (1)' }).click();
  const review = guest.locator('li.review');
  await expect(review).toContainText(REVIEW);
  await expect(review).toContainText(`Wymiana: ${ITEM}`);
  await expect(review.getByRole('img', { name: 'Ocena 5 na 5' })).toBeVisible();

  for (const context of [sellerContext, buyerContext, adminContext, guestContext]) await context.close();
});
