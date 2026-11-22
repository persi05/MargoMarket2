package com.margomarket.monitoring;

import com.margomarket.catalog.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionRequest;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.listing.auction.AuctionService;
import com.margomarket.user.User;
import com.margomarket.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
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
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuctionService auctions;
    @Autowired MarketSnapshot snapshot;

    long item, world;
    User seller;

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
    void healthIsPublicButShowsOnlyTheStatus() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void adminSeesHealthDetails() throws Exception {
        mvc.perform(get("/actuator/health").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.auctionClosing.status").value("UP"))
                .andExpect(jsonPath("$.components.mail").doesNotExist());
    }

    @Test
    void metricsAndModuleMapAreForAdminsOnly() throws Exception {
        for (String path : new String[] {"/actuator/prometheus", "/actuator/metrics", "/actuator/modulith", "/actuator/info"}) {
            // Guests are refused like on /api (the app answers 403 without a token).
            mvc.perform(get(path)).andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
            mvc.perform(get(path).with(user("user").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/actuator/modulith").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("conversation")));
    }

    @Test
    void prometheusExportsMarketGauges() throws Exception {
        long before = snapshot.refresh().activeAuctions();
        auction();
        snapshot.refresh();
        mvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("margomarket_auctions_active{application=\"margomarket\"} " + (before + 1) + ".0")))
                .andExpect(content().string(containsString("margomarket_listings_active{application=\"margomarket\",kind=\"gold\"}")))
                .andExpect(content().string(containsString("margomarket_reports_open{application=\"margomarket\",type=\"auction\"}")))
                .andExpect(content().string(containsString("http_server_requests_seconds")))
                .andExpect(content().string(not(containsString("margomarket_auctions_overdue{application=\"margomarket\"} 1.0"))));
    }

    @Test
    void auctionsLeftOpenPastTheirEndMakeTheServiceUnhealthy() throws Exception {
        long id = auction();
        jdbc.update("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()-interval '10 minutes',"
                + "ends_at=clock_timestamp()-interval '10 minutes' WHERE id=?", id);
        try {
            mvc.perform(get("/actuator/health").with(user("admin").roles("ADMIN"))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.components.auctionClosing.status").value("DOWN"))
                    .andExpect(jsonPath("$.components.auctionClosing.details.overdueAuctions").value(1));
            // Readiness covers only the app state and the database, so the instance keeps serving.
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
            assertThat(snapshot.counts().overdueAuctions()).isEqualTo(1);
        } finally {
            jdbc.update("UPDATE auctions SET status='CANCELLED' WHERE id=?", id);
        }
    }
}
