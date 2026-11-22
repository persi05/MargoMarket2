package com.margomarket.conversation;

/**
 * Published inside the transaction in which an intermediary confirms an exchange.
 * Synchronous listeners take part in that transaction, so a failing listener undoes the confirmation.
 */
public record ExchangeConfirmed(long confirmationId, long conversationId, long buyerId, long sellerId, String itemName) {}
