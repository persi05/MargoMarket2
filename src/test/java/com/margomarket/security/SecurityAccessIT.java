package com.margomarket.security;

import com.margomarket.catalog.internal.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.user.User;
import com.margomarket.user.internal.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.*;

/**
 * Access rules for every REST endpoint. The endpoint list is read from Spring MVC, so a new
 * endpoint is checked automatically: it must be closed to guests unless it is added to
 * {@link #PUBLIC} on purpose, and every admin endpoint must reject a regular user.
 */
@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class SecurityAccessIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    /** Endpoints a guest may call. Keep in sync with SecurityConfig – adding one here is a deliberate decision. */
    static final Set<String> PUBLIC = Set.of(
            "GET /api/listings", "GET /api/listings/{id}", "GET /api/listings/gold", "GET /api/listings/gold/config",
            "GET /api/listings/{listingId}/comments",
            "GET /api/dictionaries", "GET /api/items",
            "GET /api/auctions", "GET /api/auctions/config", "GET /api/auctions/{id}", "GET /api/auctions/{id}/bids",
            "GET /api/profiles/{id}", "GET /api/profiles/{id}/listings", "GET /api/profiles/{id}/reviews",
            "POST /api/auth/login", "POST /api/auth/register", "POST /api/auth/verify-email");

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    record Endpoint(String method, String pattern) {
        String key() { return method + " " + pattern; }
        String url() { return pattern.replaceAll("\\{[^}]+}", "1"); }
        boolean admin() { return pattern.startsWith("/api/admin/") || pattern.contains("/admin/"); }
    }

    List<Endpoint> endpoints() {
        List<Endpoint> result = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (info.getPathPatternsCondition() == null) return;
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                for (RequestMethod method : methods) result.add(new Endpoint(method.name(), pattern));
            }
        });
        result.sort(Comparator.comparing(Endpoint::key));
        return result;
    }

    MockHttpServletRequestBuilder call(Endpoint endpoint) {
        return request(HttpMethod.valueOf(endpoint.method()), endpoint.url()).contentType("application/json").content("{}");
    }

    int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void readsEnoughEndpointsToBeMeaningful() {
        var keys = endpoints().stream().map(Endpoint::key).toList();
        assertThat(keys).hasSizeGreaterThan(80).containsAll(PUBLIC);
    }

    @Test
    void guestsCanOnlyReachPublicEndpoints() throws Exception {
        List<String> open = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            if (PUBLIC.contains(endpoint.key())) continue;
            int status = status(call(endpoint));
            if (status != 401 && status != 403) open.add(endpoint.key() + " -> " + status);
        }
        assertThat(open).as("endpoints reachable without logging in").isEmpty();
    }

    @Test
    void publicEndpointsDoNotAskGuestsToLogIn() throws Exception {
        List<String> broken = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            if (!PUBLIC.contains(endpoint.key())) continue;
            int status = status(call(endpoint));
            if (status == 401 || status == 403 || status >= 500) broken.add(endpoint.key() + " -> " + status);
        }
        assertThat(broken).as("public endpoints that reject guests or fail").isEmpty();
    }

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        User regular = account("user");
        List<String> open = new ArrayList<>();
        List<Endpoint> admin = endpoints().stream().filter(Endpoint::admin).toList();
        assertThat(admin).hasSizeGreaterThan(20);
        for (Endpoint endpoint : admin) {
            int status = status(call(endpoint).with(user(regular)));
            if (status != 403) open.add(endpoint.key() + " -> " + status);
        }
        assertThat(open).as("admin endpoints reachable by a regular user").isEmpty();
    }

    @Test
    void adminsCanUseAdminEndpoints() throws Exception {
        User admin = account("admin");
        assertThat(status(request(HttpMethod.GET, "/api/admin/users").with(user(admin)))).isEqualTo(200);
        assertThat(status(request(HttpMethod.GET, "/api/admin/exchange-confirmations").with(user(admin)))).isEqualTo(200);
    }

    @Test
    void forgedOrMalformedTokensAreRejected() throws Exception {
        for (String header : List.of("Bearer not-a-jwt", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.forged", "Basic YWRtaW46YWRtaW4=")) {
            assertThat(status(request(HttpMethod.GET, "/api/listings/mine").header("Authorization", header)))
                    .as(header).isIn(401, 403);
            assertThat(status(request(HttpMethod.GET, "/api/admin/users").header("Authorization", header)))
                    .as(header).isIn(401, 403);
        }
    }

    @Nested
    class Ownership {
        User owner, stranger;
        long listing, gold;

        @BeforeEach
        void data() {
            owner = account("user");
            stranger = account("user");
            listing = jdbc.queryForObject("""
                    INSERT INTO listings(user_id,item_name,item_type_id,level,rarity_id,price,currency_id,server_id,contact,status_id)
                    VALUES (?,'Cudzy miecz',(SELECT id FROM item_types ORDER BY id LIMIT 1),100,(SELECT id FROM rarities ORDER BY id LIMIT 1),100,
                        (SELECT id FROM currencies WHERE lower(name)='pln'),(SELECT id FROM servers ORDER BY id LIMIT 1),'',
                        (SELECT id FROM listing_statuses WHERE name='active')) RETURNING id
                    """, Long.class, owner.getId());
            gold = jdbc.queryForObject("""
                    INSERT INTO listings(user_id,item_name,currency_id,server_id,contact,status_id,
                        listing_kind,gold_amount,price_minor,gold_rate_mode,rate_gold_units,rate_price_minor)
                    VALUES (?,'Złoto',(SELECT id FROM currencies WHERE lower(name)='pln'),(SELECT id FROM servers ORDER BY id LIMIT 1),'',
                        (SELECT id FROM listing_statuses WHERE name='active'),'GOLD',1500000000,3450,'PLN_PER_G',1000000000,2300) RETURNING id
                    """, Long.class, owner.getId());
        }

        String listingStatus(long id) {
            return jdbc.queryForObject("SELECT s.name FROM listings l JOIN listing_statuses s ON s.id=l.status_id WHERE l.id=?", String.class, id);
        }

        @Test
        void strangerCannotChangeSomeoneElsesListing() throws Exception {
            // A valid body, so the request reaches the ownership check instead of failing validation.
            String body = "{\"itemId\":1,\"itemName\":\"Przejęty miecz\",\"itemTypeId\":1,\"level\":1,\"enhancementLevel\":0,\"bound\":false,"
                    + "\"rarityId\":1,\"price\":1,\"currencyId\":1,\"serverId\":1,\"contact\":\"\"}";
            assertThat(status(request(HttpMethod.POST, "/api/listings/" + listing + "/sold").with(user(stranger)))).isEqualTo(403);
            assertThat(status(request(HttpMethod.DELETE, "/api/listings/" + listing).with(user(stranger)))).isIn(403, 404);
            assertThat(status(request(HttpMethod.PUT, "/api/listings/" + listing).with(user(stranger))
                    .contentType("application/json").content(body))).isIn(403, 404);
            assertThat(listingStatus(listing)).isEqualTo("active");
            assertThat(jdbc.queryForObject("SELECT price FROM listings WHERE id=?", Long.class, listing)).isEqualTo(100L);
        }

        @Test
        void strangerCannotWithdrawSomeoneElsesGold() throws Exception {
            assertThat(status(request(HttpMethod.POST, "/api/listings/" + gold + "/withdraw").with(user(stranger)))).isEqualTo(403);
            assertThat(status(request(HttpMethod.POST, "/api/listings/" + gold + "/sold").with(user(stranger)))).isEqualTo(403);
            assertThat(listingStatus(gold)).isEqualTo("active");
        }

        @Test
        void outsiderCannotReadOrWriteAConversation() throws Exception {
            User buyer = account("user");
            long conversation = jdbc.queryForObject("INSERT INTO conversations(listing_id,item_name,buyer_id,seller_id) VALUES (?,'Cudzy miecz',?,?) RETURNING id",
                    Long.class, listing, buyer.getId(), owner.getId());
            assertThat(status(request(HttpMethod.GET, "/api/conversations/" + conversation).with(user(stranger)))).isIn(403, 404);
            assertThat(status(request(HttpMethod.GET, "/api/conversations/" + conversation + "/messages").with(user(stranger)))).isIn(403, 404);
            assertThat(status(request(HttpMethod.POST, "/api/conversations/" + conversation + "/messages").with(user(stranger))
                    .contentType("application/json").content("{\"body\":\"podsłuch\"}"))).isIn(403, 404);
            assertThat(status(request(HttpMethod.POST, "/api/conversations/" + conversation + "/close").with(user(stranger))
                    .contentType("application/json").content("{}"))).isIn(403, 404);
            assertThat(jdbc.queryForObject("SELECT closed_at IS NULL FROM conversations WHERE id=?", Boolean.class, conversation)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND body='podsłuch'",
                    Long.class, conversation)).isZero();
            // The participants themselves can open it.
            assertThat(status(request(HttpMethod.GET, "/api/conversations/" + conversation).with(user(buyer)))).isEqualTo(200);
        }

        @Test
        void usersCannotMarkSomeoneElsesNotificationAsRead() throws Exception {
            long notification = jdbc.queryForObject("INSERT INTO notifications(user_id,type,message) VALUES (?,'LISTING_SOLD','test') RETURNING id",
                    Long.class, owner.getId());
            assertThat(status(request(HttpMethod.POST, "/api/notifications/" + notification + "/read").with(user(stranger)))).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id=?", Boolean.class, notification)).isFalse();
        }

        @Test
        void onlyTheBuyerOfAConfirmedExchangeCanReviewIt() throws Exception {
            User buyer = account("user"), admin = account("admin");
            long conversation = jdbc.queryForObject("INSERT INTO conversations(listing_id,item_name,buyer_id,seller_id) VALUES (?,'Cudzy miecz',?,?) RETURNING id",
                    Long.class, listing, buyer.getId(), owner.getId());
            long confirmation = jdbc.queryForObject("INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by) VALUES (?,?,?,?,?) RETURNING id",
                    Long.class, conversation, listing, buyer.getId(), owner.getId(), admin.getId());
            String body = "{\"confirmationId\":" + confirmation + ",\"rating\":5}";
            for (User user : List.of(stranger, owner)) {
                assertThat(status(request(HttpMethod.POST, "/api/reviews").with(user(user)).contentType("application/json").content(body))).isEqualTo(403);
            }
            assertThat(status(request(HttpMethod.POST, "/api/reviews").with(user(buyer)).contentType("application/json").content(body))).isEqualTo(201);
        }
    }

    User account(String role) {
        String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("""
                INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)
                VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name=?),true,localtimestamp-interval '2 days') RETURNING id
                """, Long.class, name + "@example.invalid", name, name, role);
        return users.findById(id).orElseThrow();
    }
}
