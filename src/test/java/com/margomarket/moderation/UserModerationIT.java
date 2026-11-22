package com.margomarket.moderation;

import com.margomarket.catalog.ItemCatalogImporter;
import com.margomarket.conversation.ConversationService;
import com.margomarket.listing.ListingRequest;
import com.margomarket.listing.ListingService;
import com.margomarket.listing.auction.AuctionBidRateLimiter;
import com.margomarket.listing.auction.AuctionRequest;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.listing.auction.AuctionService;
import com.margomarket.listing.auction.PlaceBidRequest;
import com.margomarket.listing.comment.ListingCommentService;
import com.margomarket.listing.gold.GoldListingRequest;
import com.margomarket.listing.gold.GoldListingService;
import com.margomarket.security.JwtService;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.user.User;
import com.margomarket.user.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class UserModerationIT {
    @Container
    static final PostgreSQLContainer DB =
            new PostgreSQLContainer("postgres:17-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data","rw"))
                    .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @Autowired UserModerationService moderation;
    @Autowired ListingService listings;
    @Autowired GoldListingService gold;
    @Autowired AuctionService auctions;
    @Autowired AuctionBidRateLimiter limiter;
    @Autowired ConversationService chats;
    @Autowired ListingCommentService comments;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    User admin, target, other;
    long item, world, currency;

    @BeforeEach
    void setup() {
        admin = account("admin");
        target = account("user");
        other = account("user");
        ReflectionTestUtils.setField(auctions, "enabled", true);
        ReflectionTestUtils.setField(auctions, "plnEnabled", true);
        ReflectionTestUtils.setField(gold, "enabled", true);
        ReflectionTestUtils.setField(limiter, "spacing", 0);
        ReflectionTestUtils.setField(limiter, "maximum", 100000);
        world = jdbc.queryForObject("SELECT id FROM servers ORDER BY id LIMIT 1", Long.class);
        currency =
                jdbc.queryForObject(
                        "SELECT id FROM currencies WHERE lower(name)='pln'", Long.class);
        item =
                jdbc.queryForObject(
                        "INSERT INTO items(external_id,name,icon_url,level,item_type_id,rarity_id)"
                            + " VALUES (?,'Moderowany przedmiot','',100,(SELECT id FROM item_types"
                            + " ORDER BY id LIMIT 1),(SELECT id FROM rarities ORDER BY id LIMIT 1))"
                            + " RETURNING id",
                        Long.class,
                        Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    User account(String role) {
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " users(email,username,display_name,password,role_id,email_verified,created_at)"
                            + " VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE"
                            + " name=?),true,localtimestamp-interval '2 days') RETURNING id",
                        Long.class,
                        name + "@example.invalid",
                        name,
                        name,
                        role);
        return users.findById(id).orElseThrow();
    }

    ListingRequest listing() {
        return new ListingRequest(
                item, "Przedmiot", 1L, 100, 0, false, 1L, 100L, currency, world, "");
    }

    AuctionRequest auction() {
        return new AuctionRequest(
                item, world, 0, false, "PLN", 1000L, Instant.now().plusSeconds(86400), "Opis", "");
    }

    GoldListingRequest gold() {
        return new GoldListingRequest(
                world, 2000000000L, "PLN_PER_G", 1000000000L, 3000L, "Pakiet", "");
    }

    void block(User user, ModerationScope scope) {
        moderation.restrict(
                user.getId(),
                Set.of(scope),
                "Powód decyzji",
                new UserModerationService.Term(3600L, null, false),
                admin);
    }

    long activeId(User user, ModerationScope scope) {
        return moderation.status(user.getId()).active().stream()
                .filter(a -> a.scope() == scope)
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void onlyAdminsCanModerateAndReadHistory() throws Exception {
        assertThatThrownBy(() -> moderation.warn(target.getId(), "Powód", other))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(
                        () ->
                                moderation.restrict(
                                        admin.getId(),
                                        Set.of(ModerationScope.ACCOUNT),
                                        "Powód",
                                        new UserModerationService.Term(null, null, true),
                                        admin))
                .isInstanceOf(ForbiddenOperationException.class);
        mvc.perform(get("/api/admin/users/" + target.getId() + "/moderation").with(user(other)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/users/" + target.getId() + "/moderation/warnings")
                                .with(user(other))
                                .contentType("application/json")
                                .content("{\"reason\":\"Powód\"}"))
                .andExpect(status().isForbidden());
        assertThat(moderation.status(target.getId()).active()).isEmpty();
    }

    @Test
    void warningsAreAuditedAndDeliveredWithoutBlockingFeatures() {
        String reason = "a".repeat(1000);
        moderation.warn(target.getId(), reason, admin);
        assertThat(moderation.status(target.getId()).active()).isEmpty();
        assertThat(moderation.overview(target.getId(), 1, admin).history().content())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.kind()).isEqualTo("WARNING");
                            assertThat(a.reason()).isEqualTo(reason);
                        });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT message FROM notifications WHERE user_id=? AND"
                                    + " type='ACCOUNT_WARNING'",
                                String.class,
                                target.getId()))
                .contains(reason);
        assertThat(listings.createListing(listing(), target).getId()).isPositive();
    }

    @Test
    void itemGoldAndAuctionPublishingRestrictionsAreIndependent() {
        block(target, ModerationScope.ITEM_LISTINGS);
        assertThatThrownBy(() -> listings.createListing(listing(), target))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(auctions.create(auction(), target).id()).isPositive();
        assertThat(gold.create(gold(), target).getId()).isPositive();
        block(other, ModerationScope.AUCTION_LISTINGS);
        assertThatThrownBy(() -> auctions.create(auction(), other))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(listings.createListing(listing(), other).getId()).isPositive();
        block(other, ModerationScope.GOLD_LISTINGS);
        assertThatThrownBy(() -> gold.create(gold(), other))
                .isInstanceOf(UserRestrictedException.class);
    }

    @Test
    void editingCannotBypassPublishingRestrictions() {
        var regular = listings.createListing(listing(), target);
        var golden = gold.create(gold(), target);
        var auction = auctions.create(auction(), target);
        for (var scope :
                List.of(
                        ModerationScope.ITEM_LISTINGS,
                        ModerationScope.GOLD_LISTINGS,
                        ModerationScope.AUCTION_LISTINGS)) block(target, scope);
        assertThatThrownBy(() -> listings.updateListing(regular.getId(), listing(), target))
                .isInstanceOf(UserRestrictedException.class);
        assertThatThrownBy(() -> gold.update(golden.getId(), gold(), target))
                .isInstanceOf(UserRestrictedException.class);
        assertThatThrownBy(() -> auctions.update(auction.id(), auction(), target))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(gold.withdraw(golden.getId(), target).getStatus().getName())
                .isEqualTo("withdrawn");
    }

    @Test
    void biddingBlockCanBeRevokedWithoutPreventingSelling() {
        long id = auctions.create(auction(), other).id();
        block(target, ModerationScope.BIDDING);
        assertThatThrownBy(
                        () ->
                                auctions.bid(
                                        id,
                                        new PlaceBidRequest(1000L, "PLN", UUID.randomUUID()),
                                        target))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(auctions.create(auction(), target).id()).isPositive();
        moderation.revoke(
                target.getId(),
                activeId(target, ModerationScope.BIDDING),
                "Wyjaśniono sytuację",
                admin);
        assertThat(
                        auctions.bid(
                                        id,
                                        new PlaceBidRequest(1000L, "PLN", UUID.randomUUID()),
                                        target)
                                .amount())
                .isEqualTo(1000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notifications WHERE user_id=? AND"
                                    + " type='ACCOUNT_RESTRICTION_REVOKED'",
                                Long.class,
                                target.getId()))
                .isEqualTo(1);
    }

    @Test
    void messageAndCommentBlocksApplyToNewAndExistingConversations() {
        long id = listings.createListing(listing(), other).getId();
        var conversation = chats.start(id, "Pierwsza wiadomość", target);
        block(target, ModerationScope.MESSAGES);
        assertThatThrownBy(() -> chats.send(conversation.id(), "Następna", target))
                .isInstanceOf(UserRestrictedException.class);
        assertThatThrownBy(() -> chats.start(id, "Nowa", target))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(comments.addComment(id, "Pytanie o przedmiot", target)).isNotNull();
        block(target, ModerationScope.COMMENTS);
        assertThatThrownBy(() -> comments.addComment(id, "Kolejne pytanie", target))
                .isInstanceOf(UserRestrictedException.class);
        assertThat(chats.messages(conversation.id(), 1, target).getContent()).isNotEmpty();
    }

    @Test
    void accountBlockStopsExistingTokensAndShowsReasonUntilRevoked() throws Exception {
        String token = jwt.generateToken(target);
        block(target, ModerationScope.ACCOUNT);
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.reason").value("Powód decyzji"));
        mvc.perform(get("/api/auth/moderation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountBlock.scope").value("ACCOUNT"));
        jdbc.update(
                "UPDATE users SET password=? WHERE id=?",
                passwords.encode("SecretPassword!123"),
                target.getId());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\""
                                                + target.getUsername()
                                                + "\",\"password\":\"SecretPassword!123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        long id = activeId(target, ModerationScope.ACCOUNT);
        moderation.revoke(target.getId(), id, "Przywrócono dostęp", admin);
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredBlocksStopApplyingAndPermanentBlocksHaveNoEnd() throws Exception {
        String token = jwt.generateToken(target);
        block(target, ModerationScope.ACCOUNT);
        jdbc.update(
                "UPDATE user_moderation_actions SET created_at=clock_timestamp()-interval '2"
                    + " hours',expires_at=clock_timestamp()-interval '1 second' WHERE user_id=?",
                target.getId());
        jdbc.update(
                "UPDATE users SET account_block_until=clock_timestamp()-interval '1 second' WHERE"
                    + " id=?",
                target.getId());
        assertThat(moderation.status(target.getId()).active()).isEmpty();
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        moderation.restrict(
                target.getId(),
                Set.of(ModerationScope.ACCOUNT),
                "Powód stałej blokady",
                new UserModerationService.Term(null, null, true),
                admin);
        assertThat(moderation.status(target.getId()).accountBlock().expiresAt()).isNull();
        target.setDisplayName("Zmieniona nazwa");
        users.saveAndFlush(target);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT account_block_id IS NOT NULL FROM users WHERE id=?",
                                Boolean.class,
                                target.getId()))
                .isTrue();
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentDecisionsLeaveOneCurrentBlockAndPreserveHistory() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                block(target, ModerationScope.BIDDING);
                                return true;
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                block(target, ModerationScope.BIDDING);
                                return true;
                            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(moderation.status(target.getId()).active()).hasSize(1);
        assertThat(moderation.overview(target.getId(), 1, admin).history().content()).hasSize(2);
        assertThatThrownBy(
                        () ->
                                moderation.restrict(
                                        target.getId(),
                                        Set.of(ModerationScope.BIDDING),
                                        "Powód",
                                        new UserModerationService.Term(
                                                60L, Instant.now().plusSeconds(60), false),
                                        admin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
