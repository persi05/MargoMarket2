package com.margomarket.monitoring.internal;

import com.margomarket.catalog.internal.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionRequest;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.listing.auction.AuctionService;
import com.margomarket.user.User;
import com.margomarket.user.internal.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Actuator runs on its own port: open there, absent from the public API port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("auction-test")
@AutoConfigureObservability // @SpringBootTest turns metric exporters off unless asked
@Testcontainers
class MonitoringIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuctionService auctions;
    @Autowired MarketSnapshot snapshot;
    @LocalServerPort int apiPort;
    @LocalManagementPort int managementPort;

    final HttpClient http = HttpClient.newHttpClient();
    long item, world;
    User seller;

    record Reply(int status, String body) {}

    Reply get(int port, String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(auctions, "enabled", true);
        ReflectionTestUtils.setField(auctions, "plnEnabled", true);
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at) "
                + "VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name='user'),true,localtimestamp-interval '2 days') RETURNING id",
                Long.class, name + "@example.invalid", name, name);
        seller = users.findById(id).orElseThrow();
        world = jdbc.queryForObject("SELECT id FROM servers ORDER BY id LIMIT 1", Long.class);
        item = jdbc.queryForObject("INSERT INTO items(external_id,name,icon_url,level,item_type_id,rarity_id) VALUES (?,'Miecz monitorowany','',100,"
                + "(SELECT id FROM item_types WHERE name='Jednoręczne'),(SELECT id FROM rarities WHERE name='Heroiczny')) RETURNING id",
                Long.class, Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    long auction() {
        return auctions.create(new AuctionRequest(item, world, 0, false, "PLN", 1000L, Instant.now().plusSeconds(86400), "Opis", ""), seller).id();
    }

    @Test
    void actuatorIsNotReachableThroughThePublicPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(apiPort);
        for (String path : new String[] {"/actuator/health", "/actuator/prometheus", "/actuator/metrics", "/actuator/modulith"}) {
            assertThat(get(apiPort, path).status()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void managementPortShowsHealthWithDetailsAndProbes() throws Exception {
        Reply health = get(managementPort, "/actuator/health");
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body()).contains("\"db\"").contains("\"auctionClosing\"").doesNotContain("\"mail\"");
        assertThat(get(managementPort, "/actuator/health/liveness").status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness").status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/modulith").body()).contains("conversation");
    }

    @Test
    void prometheusExportsMarketGauges() throws Exception {
        long before = snapshot.refresh().activeAuctions();
        auction();
        snapshot.refresh();
        assertThat(get(apiPort, "/api/dictionaries").status()).isEqualTo(200); // so the HTTP timer exists
        Reply metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.status()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("margomarket_auctions_active{application=\"margomarket\"} " + (before + 1) + ".0")
                .contains("margomarket_listings_active{application=\"margomarket\",kind=\"gold\"}")
                .contains("margomarket_reports_open{application=\"margomarket\",type=\"auction\"}")
                .contains("http_server_requests_seconds");
    }

    @Test
    void auctionsLeftOpenPastTheirEndMakeTheServiceUnhealthy() throws Exception {
        long id = auction();
        jdbc.update("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()-interval '10 minutes',"
                + "ends_at=clock_timestamp()-interval '10 minutes' WHERE id=?", id);
        try {
            Reply health = get(managementPort, "/actuator/health");
            assertThat(health.status()).isEqualTo(503);
            assertThat(health.body()).contains("\"overdueAuctions\":1");
            // Readiness covers only the app state and the database, so the instance keeps serving.
            assertThat(get(managementPort, "/actuator/health/readiness").status()).isEqualTo(200);
        } finally {
            jdbc.update("UPDATE auctions SET status='CANCELLED' WHERE id=?", id);
        }
    }
}
