package com.margomarket.moderation;

/**
 * Published after a user's restrictions or account block change. Listeners run after the commit,
 * e.g. to refresh the user's open pages and close the sessions of a blocked account.
 */
public record UserModerationChanged(long userId) {}
