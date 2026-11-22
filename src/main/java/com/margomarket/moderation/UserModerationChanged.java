package com.margomarket.moderation;

/**
 * Published when a user's restrictions or account block change. Listeners run after the commit
 * (through the outbox), e.g. to refresh the user's open pages and close the sessions of a blocked account.
 */
public record UserModerationChanged(long userId) {}
