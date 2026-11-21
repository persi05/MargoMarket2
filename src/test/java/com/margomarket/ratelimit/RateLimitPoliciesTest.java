package com.margomarket.ratelimit;

import static com.margomarket.ratelimit.RateLimitPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitPoliciesTest {
    @Test
    void authEndpointsUseTheirOwnLimitBeforeTheGeneralOne() {
        assertThat(RateLimitPolicies.match("POST", "/api/auth/login")).containsExactly(LOGIN, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auth/register")).containsExactly(REGISTER, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auth/verify-email")).containsExactly(VERIFY_EMAIL, WRITE);
    }

    @Test
    void chatConversationsAndReportsHaveDedicatedLimits() {
        assertThat(RateLimitPolicies.match("POST", "/api/conversations/12/messages")).containsExactly(CHAT_MESSAGE, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/conversations")).containsExactly(CONVERSATION_START, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/conversations/12/reports")).containsExactly(REPORT, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auctions/3/reports")).containsExactly(REPORT, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auctions/3/bids/9/reports")).containsExactly(REPORT, WRITE);
    }

    @Test
    void creatingItemGoldAndAuctionListingsSharesOneLimit() {
        assertThat(RateLimitPolicies.match("POST", "/api/listings")).containsExactly(LISTING_CREATE, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/listings/gold")).containsExactly(LISTING_CREATE, WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auctions")).containsExactly(LISTING_CREATE, WRITE);
        assertThat(RateLimitPolicies.match("PUT", "/api/listings/5")).containsExactly(WRITE);
        assertThat(RateLimitPolicies.match("POST", "/api/auctions/5/bids")).containsExactly(WRITE);
    }

    @Test
    void readsUseTheReadLimitAndNonApiPathsAreIgnored() {
        assertThat(RateLimitPolicies.match("GET", "/api/items")).containsExactly(READ);
        assertThat(RateLimitPolicies.match("GET", "/api/conversations/12/messages")).containsExactly(READ);
        assertThat(RateLimitPolicies.match("OPTIONS", "/api/items")).isEmpty();
        assertThat(RateLimitPolicies.match("GET", "/ws/conversations")).isEmpty();
        assertThat(RateLimitPolicies.match("GET", "/apiary")).isEmpty();
    }
}
