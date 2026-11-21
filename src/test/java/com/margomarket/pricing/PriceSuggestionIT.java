package com.margomarket.pricing;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.margomarket.dto.AuctionRequest;
import com.margomarket.dto.PlaceBidRequest;
import com.margomarket.model.User;
import com.margomarket.repository.UserRepository;
import com.margomarket.security.JwtService;
import com.margomarket.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class PriceSuggestionIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(Duration.ofMinutes(3));

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
    @Autowired JwtService jwt;
    @Autowired AuctionService auctions;
    @Autowired AuctionClosingService closing;
    @Autowired AuctionBidRateLimiter limiter;

    private User seller, buyer;
    private long item, classic, lelwani, fobos;
    private String token;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(limiter, "spacing", 0);
        ReflectionTestUtils.setField(limiter, "maximum", 100000);
        ReflectionTestUtils.setField(auctions, "enabled", true);
        ReflectionTestUtils.setField(auctions, "plnEnabled", true);
        seller = account();
        buyer = account();
        token = "Bearer " + jwt.generateToken(buyer);
        classic = world("Classic");
        lelwani = world("Lelwani");
        fobos = world("Fobos");
        item = jdbc.queryForObject("INSERT INTO items(external_id,name,icon_url,level,item_type_id,rarity_id)"
                        + " VALUES (?,'Legenda testowa','',40,(SELECT id FROM item_types WHERE name='Jednoręczne'),"
                        + "(SELECT id FROM rarities WHERE name='Legendarny')) RETURNING id",
                Long.class, Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    private long world(String name) {
        return jdbc.queryForObject("SELECT id FROM servers WHERE name=?", Long.class, name);
    }

    private User account() {
        String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)"
                        + " VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name='user'),true,localtimestamp-interval '2 days') RETURNING id",
                Long.class, name + "@example.invalid", name, name);
        return users.findById(id).orElseThrow();
    }

    private void listing(long world, String status, String currency, int price, int enhancement) {
        jdbc.update("""
                INSERT INTO listings(user_id,item_name,item_id,item_type_id,level,rarity_id,price,currency_id,server_id,
                    contact,status_id,enhancement_level,sold_at)
                SELECT ?, i.name, i.id, i.item_type_id, i.level, i.rarity_id, ?, (SELECT id FROM currencies WHERE name=?), ?,
                    '', (SELECT id FROM listing_statuses WHERE name=?), ?, CASE WHEN ?='sold' THEN localtimestamp END
                FROM items i WHERE i.id=?
                """, seller.getId(), price, currency, world, status, enhancement, status, item);
    }

    private void wonAuction(long grosze) {
        long id = auctions.create(new AuctionRequest(item, classic, 0, false, "PLN", 1000L,
                Instant.now().plusSeconds(86400), "Opis testu", ""), seller).id();
        auctions.bid(id, new PlaceBidRequest(grosze, "PLN", UUID.randomUUID()), buyer);
        jdbc.update("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()-interval '1 second',"
                + "ends_at=clock_timestamp()-interval '1 second' WHERE id=?", id);
        closing.closeDue();
    }

    private void marketData() {
        listing(classic, "sold", "pln", 100, 0);
        listing(classic, "sold", "pln", 100, 0);
        listing(lelwani, "sold", "w grze", 1_900_000_000, 0); // 1,9g at 19m per zł = 100 zł
        listing(classic, "active", "pln", 300, 0);
        listing(fobos, "sold", "pln", 5_000, 0);
        jdbc.update("UPDATE listings SET created_at=created_at-interval '1 day', sold_at=sold_at-interval '1 day' WHERE item_id=?", item);
        wonAuction(11_000);
    }

    @Test
    void suggestsPricesFromSalesAuctionsAndOffersOfTheSharedMarket() throws Exception {
        marketData();
        mvc.perform(get("/api/prices/items/{id}", item).param("serverId", String.valueOf(classic)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worldType").value("TRANSFERABLE"))
                .andExpect(jsonPath("$.levels.length()").value(6))
                .andExpect(jsonPath("$.levels[0].basis").value("DIRECT"))
                .andExpect(jsonPath("$.levels[0].sampleCount").value(5))
                .andExpect(jsonPath("$.levels[0].totalGrosze").value(10_000))
                .andExpect(jsonPath("$.levels[0].totalGold").value(1_900_000_000L))
                // +3 = 100 zł + 748 000 points at 110 zł per million on Classic.
                .andExpect(jsonPath("$.levels[3].basis").value("ESTIMATED"))
                .andExpect(jsonPath("$.levels[3].upgradeGrosze").value(8_228))
                .andExpect(jsonPath("$.levels[3].totalGrosze").value(18_228));
    }

    @Test
    void isolatedWorldsOnlyUseTheirOwnPrices() throws Exception {
        marketData();
        mvc.perform(get("/api/prices/items/{id}", item).param("serverId", String.valueOf(fobos)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketWorlds").value(containsInAnyOrder("Fobos")))
                .andExpect(jsonPath("$.levels[0].totalGrosze").value(500_000))
                .andExpect(jsonPath("$.levels[0].lowData").value(true));
    }

    @Test
    void historyListsTheMarketNewestFirst() throws Exception {
        marketData();
        mvc.perform(get("/api/prices/items/{id}/history", item).param("serverId", String.valueOf(classic)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].source").value("AUCTION"))
                .andExpect(jsonPath("$.content[0].plnGrosze").value(11_000))
                .andExpect(jsonPath("$.content[*].serverName").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.oneOf("Classic", "Lelwani"))));
        mvc.perform(get("/api/prices/items/{id}/history", item).header("Authorization", token))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void pricesRequireSignIn() throws Exception {
        mvc.perform(get("/api/prices/items/{id}", item).param("serverId", String.valueOf(classic)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unknownItemOrWorldIsNotFound() throws Exception {
        mvc.perform(get("/api/prices/items/{id}", 99_999_999).param("serverId", String.valueOf(classic)).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prices/items/{id}", item).param("serverId", "99999").header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
