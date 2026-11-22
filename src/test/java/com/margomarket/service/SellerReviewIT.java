package com.margomarket.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.margomarket.dto.SellerReviewDtos.CreateReviewRequest;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.UserRestrictedException;
import com.margomarket.model.User;
import com.margomarket.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.*;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class SellerReviewIT {
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
    @Autowired SellerReviewService reviews;
    @Autowired UserModerationService moderation;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    User admin, seller, buyer, other;

    @BeforeEach
    void setup() {
        admin = account("admin");
        seller = account("user");
        buyer = account("user");
        other = account("user");
    }

    @Test
    void buyerReviewsConfirmedExchangeOnceAndSellerIsNotified() {
        long confirmation = confirmedExchange(seller, buyer);
        assertThat(reviews.pending(buyer)).singleElement().satisfies(p -> {
            assertThat(p.confirmationId()).isEqualTo(confirmation);
            assertThat(p.conversationId()).isEqualTo(jdbc.queryForObject("SELECT conversation_id FROM exchange_confirmations WHERE id=?", Long.class, confirmation));
        });

        var review = reviews.create(buyer, new CreateReviewRequest(confirmation, 5, "  Szybka i uczciwa wymiana  "));
        assertThat(review.comment()).isEqualTo("Szybka i uczciwa wymiana");

        var page = reviews.forSeller(seller.getId(), 1);
        assertThat(page.summary().count()).isEqualTo(1);
        assertThat(page.summary().average()).isEqualTo(5.0);
        assertThat(page.summary().distribution()).containsExactly(0L, 0L, 0L, 0L, 1L);
        assertThat(page.reviews().content()).singleElement().satisfies(r -> {
            assertThat(r.buyerId()).isEqualTo(buyer.getId());
            assertThat(r.itemName()).isEqualTo("Recenzowany miecz");
        });
        assertThat(reviews.pending(buyer)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='SELLER_REVIEW'", Long.class, seller.getId())).isEqualTo(1L);

        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 1, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("już wystawiona");
    }

    @Test
    void onlyTheConfirmedBuyerCanReview() {
        long confirmation = confirmedExchange(seller, buyer);
        assertThatThrownBy(() -> reviews.create(seller, new CreateReviewRequest(confirmation, 5, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> reviews.create(other, new CreateReviewRequest(confirmation, 5, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(reviews.forSeller(seller.getId(), 1).summary().count()).isZero();
    }

    @Test
    void validatesRatingAndCommentLength() {
        long confirmation = confirmedExchange(seller, buyer);
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 6, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 4, "x".repeat(1001))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokedConfirmationBlocksNewReviewsAndRemovesExistingOnesFromReputation() {
        long reviewed = confirmedExchange(seller, buyer);
        reviews.create(buyer, new CreateReviewRequest(reviewed, 1, null));
        long unreviewed = confirmedExchange(seller, buyer);
        jdbc.update("UPDATE exchange_confirmations SET revoked_at=clock_timestamp(),revoked_by=?,revoke_reason='test' WHERE id IN (?,?)", admin.getId(), reviewed, unreviewed);

        assertThat(reviews.forSeller(seller.getId(), 1).summary().count()).isZero();
        assertThat(reviews.forSeller(seller.getId(), 1).reviews().content()).isEmpty();
        assertThat(reviews.pending(buyer)).isEmpty();
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(unreviewed, 5, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unieważnione");
    }

    @Test
    void reviewWindowCloses() {
        long confirmation = confirmedExchange(seller, buyer);
        jdbc.update("UPDATE exchange_confirmations SET confirmed_at=clock_timestamp()-interval '31 days' WHERE id=?", confirmation);
        assertThat(reviews.pending(buyer)).isEmpty();
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 5, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Czas na wystawienie opinii minął");
    }

    @Test
    void reviewRestrictionBlocksPosting() {
        long confirmation = confirmedExchange(seller, buyer);
        moderation.restrict(buyer.getId(), Set.of(ModerationScope.REVIEWS), "Fałszywe opinie",
                new UserModerationService.Term(3600L, null, false), admin);
        assertThatThrownBy(() -> reviews.create(buyer, new CreateReviewRequest(confirmation, 5, null)))
                .isInstanceOf(UserRestrictedException.class);
    }

    @Test
    void hiddenReviewLeavesReputation() {
        long first = confirmedExchange(seller, buyer);
        long second = confirmedExchange(seller, buyer);
        long hidden = reviews.create(buyer, new CreateReviewRequest(first, 1, "obraźliwy wpis")).id();
        reviews.create(buyer, new CreateReviewRequest(second, 4, null));

        assertThatThrownBy(() -> reviews.hide(hidden, other, "powód")).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> reviews.hide(hidden, admin, " ")).isInstanceOf(IllegalArgumentException.class);
        reviews.hide(hidden, admin, "Naruszenie regulaminu");

        var summary = reviews.forSeller(seller.getId(), 1).summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.average()).isEqualTo(4.0);
        assertThat(summary.positivePercent()).isEqualTo(100);
    }

    @Test
    void databaseRejectsReviewsThatDoNotMatchTheConfirmationAndEdits() {
        long confirmation = confirmedExchange(seller, buyer);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO seller_reviews(confirmation_id,seller_id,buyer_id,rating) VALUES (?,?,?,5)",
                confirmation, seller.getId(), other.getId())).isInstanceOf(DataIntegrityViolationException.class);
        long id = reviews.create(buyer, new CreateReviewRequest(confirmation, 2, null)).id();
        assertThatThrownBy(() -> jdbc.update("UPDATE seller_reviews SET rating=5 WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void endpointsFollowAccessRules() throws Exception {
        long confirmation = confirmedExchange(seller, buyer);
        mvc.perform(post("/api/reviews").contentType("application/json")
                        .content("{\"confirmationId\":" + confirmation + ",\"rating\":5}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/reviews").with(user(buyer)).contentType("application/json")
                        .content("{\"confirmationId\":" + confirmation + ",\"rating\":5,\"comment\":\"OK\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/profiles/" + seller.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.reviews.content[0].comment").value("OK"));
        long id = jdbc.queryForObject("SELECT id FROM seller_reviews WHERE confirmation_id=?", Long.class, confirmation);
        mvc.perform(post("/api/admin/reviews/" + id + "/hide").with(user(other)).contentType("application/json")
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    long confirmedExchange(User seller, User buyer) {
        long listing = jdbc.queryForObject("""
                INSERT INTO listings(user_id,item_name,item_type_id,level,rarity_id,price,currency_id,server_id,contact,status_id)
                VALUES (?,'Recenzowany miecz',(SELECT id FROM item_types ORDER BY id LIMIT 1),100,
                    (SELECT id FROM rarities ORDER BY id LIMIT 1),100,(SELECT id FROM currencies WHERE lower(name)='pln'),
                    (SELECT id FROM servers ORDER BY id LIMIT 1),'',(SELECT id FROM listing_statuses WHERE name='sold')) RETURNING id
                """, Long.class, seller.getId());
        long conversation = jdbc.queryForObject("INSERT INTO conversations(listing_id,item_name,buyer_id,seller_id) VALUES (?,'Recenzowany miecz',?,?) RETURNING id",
                Long.class, listing, buyer.getId(), seller.getId());
        return jdbc.queryForObject("INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by) VALUES (?,?,?,?,?) RETURNING id",
                Long.class, conversation, listing, buyer.getId(), seller.getId(), admin.getId());
    }

    User account(String role) {
        String name = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("""
                INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)
                VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name=?),true,localtimestamp-interval '2 days') RETURNING id
                """, Long.class, name + "@example.invalid", name, name, role);
        return users.findById(id).orElseThrow();
    }
}
