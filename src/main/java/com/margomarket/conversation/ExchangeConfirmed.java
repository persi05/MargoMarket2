package com.margomarket.conversation;

/**
 * Published when an intermediary confirms an exchange. The event is stored in the outbox
 * (event_publication) with the confirmation; listeners run after the commit and are retried
 * if they fail, without undoing the confirmation.
 */
public record ExchangeConfirmed(long confirmationId, long conversationId, long buyerId, long sellerId, String itemName) {}
