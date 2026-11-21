package com.margomarket.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Map;

class RateLimitFilterTest {
    private final RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, Map.of(
            RateLimitPolicy.LOGIN, new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
            RateLimitPolicy.CHAT_MESSAGE, new RateLimitProperties.Limit(1, Duration.ofMinutes(1)),
            RateLimitPolicy.READ, new RateLimitProperties.Limit(1, Duration.ofMinutes(1)))), new FakeTime());
    private final RateLimitFilter filter = new RateLimitFilter(limiter, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse send(String method, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void signIn(long id, String role) {
        var user = new User();
        user.setId(id);
        var userRole = new Role();
        userRole.setName(role);
        user.setRole(userRole);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void rejectsWithRetryAfterAndPolishMessage() throws Exception {
        assertThat(send("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        var rejected = send("POST", "/api/auth/login", "1.1.1.1");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("30");
        var body = new ObjectMapper().readTree(rejected.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("retryAfterSeconds").asLong()).isEqualTo(30);
        assertThat(body.get("message").asText()).isEqualTo("Zbyt wiele prób. Spróbuj ponownie za 30 s.");
    }

    @Test
    void ipLimitsAreSeparatePerAddress() throws Exception {
        assertThat(send("GET", "/api/items", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(send("GET", "/api/items", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(send("GET", "/api/items", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void userLimitsFollowTheAccountAcrossAddresses() throws Exception {
        signIn(7, "user");
        assertThat(send("POST", "/api/conversations/1/messages", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/conversations/1/messages", "2.2.2.2").getStatus()).isEqualTo(429);
        signIn(8, "user");
        assertThat(send("POST", "/api/conversations/1/messages", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsSkipUserLimits() throws Exception {
        for (int i = 0; i < 3; i++)
            assertThat(send("POST", "/api/conversations/1/messages", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void adminsSkipUsageLimitsButNotLoginLimits() throws Exception {
        signIn(1, "admin");
        for (int i = 0; i < 3; i++) {
            assertThat(send("POST", "/api/conversations/1/messages", "1.1.1.1").getStatus()).isEqualTo(200);
            assertThat(send("GET", "/api/items", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        send("POST", "/api/auth/login", "1.1.1.1");
        send("POST", "/api/auth/login", "1.1.1.1");
        assertThat(send("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void preflightRequestsAreNeverLimited() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(send("OPTIONS", "/api/items", "1.1.1.1").getStatus()).isEqualTo(200);
    }
}
