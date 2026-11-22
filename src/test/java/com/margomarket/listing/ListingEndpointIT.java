package com.margomarket.listing;

import com.margomarket.catalog.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.user.User;
import com.margomarket.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.*;

/** Full HTTP round trips: responses are mapped outside the transaction (open-in-view is off). */
@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class ListingEndpointIT {
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
    @Autowired MockMvc mvc;
    User owner, other;
    long listing;

    @BeforeEach
    void setup() {
        owner = account();
        other = account();
        long item = jdbc.queryForObject("""
                INSERT INTO items(external_id,name,icon_url,item_type_id,level,rarity_id)
                VALUES ((SELECT coalesce(max(external_id),0)+1000000 FROM items), 'Miecz testowy', '',
                    (SELECT id FROM item_types ORDER BY id LIMIT 1), 100, (SELECT id FROM rarities ORDER BY id LIMIT 1))
                RETURNING id
                """, Long.class);
        listing = jdbc.queryForObject("""
                INSERT INTO listings(user_id,item_id,item_name,item_type_id,level,rarity_id,price,currency_id,server_id,contact,status_id)
                SELECT ?, i.id, i.name, i.item_type_id, i.level, i.rarity_id, 100, (SELECT id FROM currencies WHERE lower(name)='pln'),
                    (SELECT id FROM servers ORDER BY id LIMIT 1), '', (SELECT id FROM listing_statuses WHERE name='active')
                FROM items i WHERE i.id=? RETURNING id
                """, Long.class, owner.getId(), item);
    }

    @Test
    void ownerMarksListingAsSoldAndGetsTheUpdatedListingBack() throws Exception {
        mvc.perform(post("/api/listings/" + listing + "/sold").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sold"))
                .andExpect(jsonPath("$.itemType.name").isNotEmpty())
                .andExpect(jsonPath("$.rarity.name").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT s.name FROM listings l JOIN listing_statuses s ON s.id=l.status_id WHERE l.id=?", String.class, listing))
                .isEqualTo("sold");
        // Marking again is harmless and still answers with the listing.
        mvc.perform(post("/api/listings/" + listing + "/sold").with(user(owner))).andExpect(status().isOk());
    }

    @Test
    void ownerEditsListingAndGetsTheUpdatedListingBack() throws Exception {
        long item = jdbc.queryForObject("SELECT item_id FROM listings WHERE id=?", Long.class, listing);
        long currency = jdbc.queryForObject("SELECT currency_id FROM listings WHERE id=?", Long.class, listing);
        long server = jdbc.queryForObject("SELECT server_id FROM listings WHERE id=?", Long.class, listing);
        mvc.perform(put("/api/listings/" + listing).with(user(owner)).contentType("application/json")
                        .content("{\"itemId\":" + item + ",\"itemName\":\"Miecz testowy\",\"itemTypeId\":1,\"level\":100,\"enhancementLevel\":0,"
                                + "\"bound\":false,\"rarityId\":1,\"price\":250,\"currencyId\":" + currency + ",\"serverId\":" + server + ",\"contact\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(250))
                .andExpect(jsonPath("$.itemType.name").isNotEmpty());
    }

    @Test
    void anotherUserCannotMarkTheListingAsSold() throws Exception {
        mvc.perform(post("/api/listings/" + listing + "/sold").with(user(other))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT s.name FROM listings l JOIN listing_statuses s ON s.id=l.status_id WHERE l.id=?", String.class, listing))
                .isEqualTo("active");
    }

    User account() {
        String name = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("""
                INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)
                VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name='user'),true,localtimestamp-interval '2 days') RETURNING id
                """, Long.class, name + "@example.invalid", name, name);
        return users.findById(id).orElseThrow();
    }
}
