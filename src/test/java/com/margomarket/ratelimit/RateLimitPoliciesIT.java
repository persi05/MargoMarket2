package com.margomarket.ratelimit;

import com.margomarket.catalog.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.security.JwtService;
import com.margomarket.user.User;
import com.margomarket.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives every rate limit through the real security chain. Capacities are lowered to 2 (general
 * writes to 5) so each limit is reached in a few requests; the defaults are checked in RateLimitPolicyTest.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.limits.login.capacity=2", "app.rate-limit.limits.login.period=1h",
        "app.rate-limit.limits.register.capacity=2", "app.rate-limit.limits.register.period=1h",
        "app.rate-limit.limits.verify-email.capacity=2", "app.rate-limit.limits.verify-email.period=1h",
        "app.rate-limit.limits.chat-message.capacity=2", "app.rate-limit.limits.chat-message.period=1h",
        "app.rate-limit.limits.conversation-start.capacity=2", "app.rate-limit.limits.conversation-start.period=1h",
        "app.rate-limit.limits.report.capacity=2", "app.rate-limit.limits.report.period=1h",
        "app.rate-limit.limits.listing-create.capacity=2", "app.rate-limit.limits.listing-create.period=1h",
        "app.rate-limit.limits.write.capacity=5", "app.rate-limit.limits.write.period=1h",
        "app.rate-limit.limits.read.capacity=2", "app.rate-limit.limits.read.period=1h"})
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class RateLimitPoliciesIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    private static final AtomicInteger ADDRESSES = new AtomicInteger(1);

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private record Call(HttpMethod method, String path, String body) {}

    private static Call post(String path, String body) { return new Call(HttpMethod.POST, path, body); }

    private String freshIp() {
        return "10.0." + (ADDRESSES.get() / 250) + "." + (ADDRESSES.getAndIncrement() % 250 + 1);
    }

    private String token(String role) {
        String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject(
                "INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)"
                        + " VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name=?),true,localtimestamp-interval '2 days')"
                        + " RETURNING id",
                Long.class, name + "@example.invalid", name, name, role);
        User user = users.findById(id).orElseThrow();
        return "Bearer " + jwt.generateToken(user);
    }

    private MvcResult send(Call call, String token, String ip) throws Exception {
        var request = request(call.method(), call.path()).with(r -> { r.setRemoteAddr(ip); return r; });
        if (call.body() != null) request.contentType(MediaType.APPLICATION_JSON).content(call.body());
        if (token != null) request.header("Authorization", token);
        return mvc.perform(request).andReturn();
    }

    /** The first {@code allowed} calls pass the limiter, the next one gets 429 with Retry-After. */
    private void assertLimited(String label, List<Call> calls, int allowed, String token, String ip) throws Exception {
        for (int i = 0; i < allowed; i++) {
            var result = send(calls.get(i % calls.size()), token, ip);
            assertThat(result.getResponse().getStatus()).as(label + " request " + (i + 1)).isNotEqualTo(429);
        }
        var rejected = send(calls.get(allowed % calls.size()), token, ip).getResponse();
        assertThat(rejected.getStatus()).as(label + " over the limit").isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).as(label + " Retry-After").isNotBlank();
        assertThat(rejected.getContentAsString()).as(label + " body").contains("RATE_LIMITED");
    }

    @Test
    void loginIsLimitedPerAddress() throws Exception {
        String ip = freshIp();
        // Different logins, so the failed-login guard does not interfere.
        var calls = List.of(
                post("/api/auth/login", "{\"username\":\"brak1\",\"password\":\"x\"}"),
                post("/api/auth/login", "{\"username\":\"brak2\",\"password\":\"x\"}"),
                post("/api/auth/login", "{\"username\":\"brak3\",\"password\":\"x\"}"));
        assertLimited("login", calls, 2, null, ip);
        assertThat(send(calls.get(0), null, freshIp()).getResponse().getStatus()).isNotEqualTo(429);
    }

    @Test
    void registrationIsLimitedPerAddress() throws Exception {
        String ip = freshIp();
        assertLimited("register", List.of(post("/api/auth/register", "{}")), 2, null, ip);
        assertThat(send(post("/api/auth/register", "{}"), null, freshIp()).getResponse().getStatus()).isNotEqualTo(429);
    }

    @Test
    void emailVerificationIsLimitedPerAddress() throws Exception {
        assertLimited("verify-email", List.of(post("/api/auth/verify-email", "{}")), 2, null, freshIp());
    }

    @Test
    void chatMessagesAreLimitedPerUser() throws Exception {
        String user = token("user");
        assertLimited("chat-message", List.of(post("/api/conversations/999999/messages", "{\"body\":\"Cześć\"}")), 2, user, freshIp());
        // Same account from another address is still limited; another account is not.
        assertThat(send(post("/api/conversations/999999/messages", "{\"body\":\"x\"}"), user, freshIp()).getResponse().getStatus()).isEqualTo(429);
        assertThat(send(post("/api/conversations/999999/messages", "{\"body\":\"x\"}"), token("user"), freshIp()).getResponse().getStatus()).isNotEqualTo(429);
    }

    @Test
    void startingConversationsIsLimitedPerUser() throws Exception {
        assertLimited("conversation-start", List.of(post("/api/conversations", "{}")), 2, token("user"), freshIp());
    }

    @Test
    void allReportEndpointsShareOneLimit() throws Exception {
        var calls = List.of(
                post("/api/conversations/999999/reports", "{}"),
                post("/api/auctions/999999/reports", "{}"),
                post("/api/auctions/999999/bids/1/reports", "{}"));
        assertLimited("report", calls, 2, token("user"), freshIp());
    }

    @Test
    void itemGoldAndAuctionListingsShareOneCreationLimit() throws Exception {
        var calls = List.of(
                post("/api/listings", "{}"),
                post("/api/listings/gold", "{}"),
                post("/api/auctions", "{}"));
        assertLimited("listing-create", calls, 2, token("user"), freshIp());
    }

    @Test
    void otherWritesUseTheGeneralWriteLimit() throws Exception {
        var calls = List.of(
                new Call(HttpMethod.PUT, "/api/listings/999999", "{}"),
                new Call(HttpMethod.DELETE, "/api/listings/999999", null),
                post("/api/listings/999999/favorite", null));
        assertLimited("write", calls, 5, token("user"), freshIp());
    }

    @Test
    void readsAreLimitedPerAddress() throws Exception {
        String ip = freshIp();
        assertLimited("read", List.of(new Call(HttpMethod.GET, "/api/dictionaries", null)), 2, null, ip);
        assertThat(send(new Call(HttpMethod.GET, "/api/dictionaries", null), null, freshIp()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminsSkipUsageLimitsButNotLoginLimits() throws Exception {
        String admin = token("admin");
        String ip = freshIp();
        for (int i = 0; i < 6; i++) {
            assertThat(send(post("/api/conversations/999999/messages", "{\"body\":\"x\"}"), admin, ip).getResponse().getStatus()).isNotEqualTo(429);
            assertThat(send(new Call(HttpMethod.GET, "/api/dictionaries", null), admin, ip).getResponse().getStatus()).isEqualTo(200);
        }
        var login = List.of(
                post("/api/auth/login", "{\"username\":\"brak4\",\"password\":\"x\"}"),
                post("/api/auth/login", "{\"username\":\"brak5\",\"password\":\"x\"}"),
                post("/api/auth/login", "{\"username\":\"brak6\",\"password\":\"x\"}"));
        assertLimited("admin login", login, 2, admin, ip);
    }
}
