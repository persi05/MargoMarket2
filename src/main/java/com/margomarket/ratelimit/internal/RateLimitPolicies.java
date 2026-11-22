package com.margomarket.ratelimit.internal;

import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

/** Maps a request to the limits it consumes: specific ones first, then the general read/write limit. */
public final class RateLimitPolicies {
    private record Rule(String method, String pattern, RateLimitPolicy policy) {}

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", RateLimitPolicy.LOGIN),
            new Rule("POST", "/api/auth/register", RateLimitPolicy.REGISTER),
            new Rule("POST", "/api/auth/verify-email", RateLimitPolicy.VERIFY_EMAIL),
            new Rule("POST", "/api/conversations/*/messages", RateLimitPolicy.CHAT_MESSAGE),
            new Rule("POST", "/api/conversations", RateLimitPolicy.CONVERSATION_START),
            new Rule("POST", "/api/conversations/*/reports", RateLimitPolicy.REPORT),
            new Rule("POST", "/api/auctions/*/reports", RateLimitPolicy.REPORT),
            new Rule("POST", "/api/auctions/*/bids/*/reports", RateLimitPolicy.REPORT),
            new Rule("POST", "/api/listings", RateLimitPolicy.LISTING_CREATE),
            new Rule("POST", "/api/listings/gold", RateLimitPolicy.LISTING_CREATE),
            new Rule("POST", "/api/auctions", RateLimitPolicy.LISTING_CREATE));

    private RateLimitPolicies() {}

    public static List<RateLimitPolicy> match(String method, String path) {
        if (path == null || !(path.equals("/api") || path.startsWith("/api/"))) return List.of();
        List<RateLimitPolicy> result = new ArrayList<>();
        for (Rule rule : RULES)
            if (rule.method().equalsIgnoreCase(method) && MATCHER.match(rule.pattern(), path)) result.add(rule.policy());
        switch (method.toUpperCase()) {
            case "GET", "HEAD" -> result.add(RateLimitPolicy.READ);
            case "POST", "PUT", "PATCH", "DELETE" -> result.add(RateLimitPolicy.WRITE);
            default -> { }
        }
        return result;
    }
}
