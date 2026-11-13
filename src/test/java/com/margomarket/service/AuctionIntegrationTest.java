package com.margomarket.service;

import com.margomarket.dto.*;
import com.margomarket.exception.*;
import com.margomarket.model.User;
import com.margomarket.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="AUCTION_TEST_DATABASE_URL",matches=".+")
class AuctionIntegrationTest {
    @Autowired AuctionService auctions;
    @Autowired AuctionClosingService closing;
    @Autowired AuctionFulfillmentService fulfillment;
    @Autowired AuctionModerationService moderation;
    @Autowired AuctionBidRateLimiter limiter;
    @Autowired ListingService listings;
    @Autowired ConversationService conversations;
    @Autowired ConversationExchangeService exchanges;
    @Autowired ExchangeConfirmationService confirmations;
    @Autowired PublicProfileService profiles;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired MockMvc mvc;
    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    private User seller,buyer,other,admin;
    private long item,world;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(limiter,"spacing",0);
        ReflectionTestUtils.setField(limiter,"maximum",100000);
        ReflectionTestUtils.setField(auctions,"enabled",true);
        ReflectionTestUtils.setField(auctions,"plnEnabled",true);
        seller=account("user"); buyer=account("user"); other=account("user"); admin=account("admin");
        world=jdbc.queryForObject("SELECT id FROM servers ORDER BY id LIMIT 1",Long.class);
        item=jdbc.queryForObject("INSERT INTO items(external_id,name,icon_url,level,item_type_id,rarity_id) VALUES (?,'Miecz testowy','',100,(SELECT id FROM item_types WHERE name='Jednoręczne'),(SELECT id FROM rarities WHERE name='Heroiczny')) RETURNING id",Long.class,Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }
    private User account(String role) {
        String name="t"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        Long id=jdbc.queryForObject("INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at) VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name=?),true,localtimestamp-interval '2 days') RETURNING id",Long.class,name+"@example.invalid",name,name,role);
        return users.findById(id).orElseThrow();
    }
    private AuctionRequest request(String currency,long amount) {
        return new AuctionRequest(item,world,0,false,currency,amount,Instant.now().plusSeconds(86400),"Opis testu","");
    }
    private long create() { return auctions.create(request("PLN",1000),seller).id(); }
    private PlaceBidRequest bid(long amount) { return new PlaceBidRequest(amount,"PLN",UUID.randomUUID()); }
    private void expire(long id) {
        jdbc.update("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()-interval '1 second',ends_at=clock_timestamp()-interval '1 second' WHERE id=?",id);
    }

    @Test void publicResponsesHideAmountsAndPrivateIdentityAndApiRequiresAuthentication() throws Exception {
        long id=create(); auctions.bid(id,bid(1000),buyer);
        var publicView=auctions.get(id,null);
        assertThat(publicView.startingAmount()).isNull(); assertThat(publicView.highestAmount()).isNull();
        assertThat(publicView.listing().price()).isNull(); assertThat(publicView.listing().sellerEmail()).isNull();
        var history=auctions.bids(id,1,null).content().getFirst();
        assertThat(history.get("amount")).isNull(); assertThat(history).doesNotContainKey("userId");
        mvc.perform(get("/api/auctions/"+id)).andExpect(status().isOk()).andExpect(jsonPath("highestAmount").doesNotExist());
        mvc.perform(post("/api/auctions/"+id+"/bids").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auctions/register/audit").with(user(buyer))).andExpect(status().isForbidden());
        assertThat(auctions.search(null,null,"",1,"ACTIVE",null).content()).extracting(AuctionResponse::id).contains(id);
    }
    @Test void validatesCurrencyAgeOwnerMinimumAndLeadingBidder() {
        long id=create();
        assertThatThrownBy(()->auctions.bid(id,bid(1000),seller)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->auctions.bid(id,bid(999),buyer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->auctions.bid(id,new PlaceBidRequest(1000L,"GOLD",UUID.randomUUID()),buyer)).isInstanceOf(IllegalArgumentException.class);
        User young=account("user"); young.setCreatedAt(LocalDateTime.now());
        assertThatThrownBy(()->auctions.bid(id,bid(1000),young)).isInstanceOf(ForbiddenOperationException.class);
        young.setCreatedAt(LocalDateTime.now().minusDays(2)); young.setEmailVerified(false);
        assertThatThrownBy(()->auctions.bid(id,bid(1000),young)).isInstanceOf(ForbiddenOperationException.class);
        auctions.bid(id,bid(1000),buyer);
        assertThatThrownBy(()->auctions.bid(id,bid(1100),buyer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->auctions.bid(id,bid(1099),other)).isInstanceOf(IllegalArgumentException.class);
        auctions.bid(id,bid(1100),other);
        assertThat(auctions.get(id,other).highestAmount()).isEqualTo(1100);
    }
    @Test void replayHasOneBidAndOneNotificationAndRejectsChangedPayload() {
        long id=create(); auctions.bid(id,bid(1000),buyer);
        var r=bid(1100); var first=auctions.bid(id,r,other); var retry=auctions.bid(id,r,other);
        assertThat(retry).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_bids WHERE auction_id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE auction_id=? AND type='AUCTION_OUTBID'",Long.class,id)).isEqualTo(1);
        assertThatThrownBy(()->auctions.bid(id,new PlaceBidRequest(1200L,"PLN",r.idempotencyKey()),other)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentEqualBidsAcceptExactlyOne() throws Exception {
        long id=create(); var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=List.of(buyer,other).stream().map(u->pool.submit(()->{ start.await(); try {auctions.bid(id,bid(1000),u);return true;} catch(IllegalArgumentException ex){return false;} })).toList();
            start.countDown(); int successes=0; for(var f:futures) if(f.get(15,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_bids WHERE auction_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void timeIsCheckedAfterWaitingForListingLock() throws Exception {
        long id=create();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor();var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) { statement.executeQuery("SELECT id FROM listings WHERE id="+id+" FOR UPDATE"); }
            var future=pool.submit(()->{ try {auctions.bid(id,bid(1000),buyer);return false;} catch(IllegalArgumentException ex){return true;} });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            boolean blocked=false;
            while(System.nanoTime()<deadline) {
                blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'SELECT id FROM listings WHERE id=%FOR UPDATE')",Boolean.class));
                if(blocked)break; Thread.sleep(20);
            }
            assertThat(blocked).isTrue();
            try(var statement=connection.createStatement()) { statement.executeUpdate("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()-interval '1 second',ends_at=clock_timestamp()-interval '1 second' WHERE id="+id); }
            connection.commit(); assertThat(future.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(auctions.get(id,buyer).bidCount()).isZero();
    }
    @Test void antiSnipingExtendsAndExpiredBidsAreRejected() {
        long id=create();
        jdbc.update("UPDATE auctions SET created_at=clock_timestamp()-interval '2 hours',original_ends_at=clock_timestamp()+interval '1 minute',ends_at=clock_timestamp()+interval '1 minute' WHERE id=?",id);
        var accepted=auctions.bid(id,bid(1000),buyer); var a=auctions.get(id,buyer);
        assertThat(a.endsAt()).isEqualTo(accepted.acceptedAt().plusSeconds(120));
        expire(id);
        assertThatThrownBy(()->auctions.bid(id,bid(1100),other)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void editsFreezeAfterFirstBidAndOrdinaryCrudCannotBypassAuctions() {
        long id=create(); auctions.update(id,request("GOLD",1500000000),seller);
        auctions.bid(id,new PlaceBidRequest(1500000000L,"GOLD",UUID.randomUUID()),buyer);
        assertThatThrownBy(()->auctions.update(id,request("PLN",1000),seller)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->listings.updateListing(id,new ListingRequest(item,"Miecz",1L,100,0,false,1L,99,1L,world,"czat"),seller)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->listings.deleteListing(id,admin)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->listings.markAsSold(id,seller)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->auctions.cancel(id,seller,"Rezygnacja")).isInstanceOf(ForbiddenOperationException.class);
    }
    @Test void closingIsIdempotentAndDoesNotConfirmOrSellAndOnlyWinnerCanStartContact() {
        long id=create(); auctions.bid(id,bid(1000),buyer); expire(id);
        closing.closeDue(); closing.closeDue();
        var a=auctions.get(id,seller); assertThat(a.status()).isEqualTo("ENDED_WITH_WINNER");
        assertThat(a.fulfillmentStatus()).isEqualTo("PENDING_CONTACT");
        assertThat(a.listing().status()).isEqualTo("auction_ended");
        assertThat(profiles.get(seller.getId()).confirmedSales()).isZero();
        assertThat(profiles.get(seller.getId()).endedAuctions()).isEqualTo(1);
        assertThatThrownBy(()->fulfillment.start(id,other)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->conversations.start(id,"Obejście",other)).isInstanceOf(ForbiddenOperationException.class);
        long chat=fulfillment.start(id,buyer); assertThat(fulfillment.start(id,seller)).isEqualTo(chat);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE listing_id=?",Long.class,id)).isEqualTo(1);
        fulfillment.action(id,seller,"DECLARE_COMPLETED",null);
        assertThat(profiles.get(seller.getId()).confirmedSales()).isZero();
        exchanges.requestIntermediary(chat,buyer); exchanges.joinAsIntermediary(chat,admin); confirmations.confirm(chat,admin);
        assertThat(profiles.get(seller.getId()).confirmedSales()).isEqualTo(1);
        assertThat(auctions.get(id,seller).fulfillmentStatus()).isEqualTo("ADMIN_CONFIRMED");
        long confirmation=jdbc.queryForObject("SELECT id FROM exchange_confirmations WHERE listing_id=?",Long.class,id);
        confirmations.revoke(confirmation,admin,"Uzasadnione ponowne rozpatrzenie");
        assertThat(profiles.get(seller.getId()).confirmedSales()).isZero();
        assertThat(auctions.get(id,seller).fulfillmentStatus()).isEqualTo("DISPUTED");
        assertThatThrownBy(()->conversations.start(id,"Nowa rozmowa",other)).isInstanceOf(ForbiddenOperationException.class);
    }
    @Test void noBidsEndWithoutFulfillmentAndBidHistoryCannotBeChanged() {
        long empty=create(); expire(empty); closing.closeDue();
        assertThat(auctions.get(empty,seller).status()).isEqualTo("ENDED_NO_BIDS");
        long id=create(); var receipt=auctions.bid(id,bid(1000),buyer);
        assertThatThrownBy(()->jdbc.update("UPDATE auction_bids SET amount=1 WHERE id=?",receipt.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM auction_bids WHERE id=?",receipt.id())).isInstanceOf(DataAccessException.class);
    }
    @Test void invalidationIsAuditedAndDoesNotReassignAnEndedWinner() {
        long id=create(); var first=auctions.bid(id,bid(1000),buyer); var top=auctions.bid(id,bid(1100),other);
        moderation.invalidate(id,top.id(),admin,"Udokumentowane nadużycie");
        assertThat(auctions.get(id,buyer).highestAmount()).isEqualTo(1000);
        expire(id); closing.closeDue(); moderation.invalidate(id,first.id(),admin,"Potwierdzony problem");
        assertThat(auctions.get(id,seller).winnerId()).isEqualTo(buyer.getId());
        assertThat(auctions.get(id,seller).fulfillmentStatus()).isEqualTo("DISPUTED");
        assertThatThrownBy(()->fulfillment.start(id,buyer)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectedAttemptsConsumePersistentRateLimitAndRestrictionsHaveAppeal() {
        long id=create(); ReflectionTestUtils.setField(limiter,"maximum",2);
        assertThatThrownBy(()->auctions.bid(id,bid(1),buyer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->auctions.bid(id,bid(1),buyer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->auctions.bid(id,bid(1000),buyer)).isInstanceOf(AuctionRateLimitException.class);
        ReflectionTestUtils.setField(limiter,"maximum",100000);
        ReflectionTestUtils.setField(limiter,"spacing",2);
        assertThat(limiter.attempt(other.getId(),id)).isZero(); assertThat(limiter.attempt(other.getId(),id)).isPositive();
        ReflectionTestUtils.setField(limiter,"spacing",0);
        moderation.restrict(other.getId(),admin,"Udokumentowany spam","Kontakt z moderacją w serwisie",Instant.now().plusSeconds(3600));
        assertThatThrownBy(()->auctions.bid(id,bid(1000),other)).isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("Odwołanie");
    }
    @Test void notificationFailureRollsBackBidAndLeaderAndFlagsBlockDirectApi() {
        long id=create(); auctions.bid(id,bid(1000),buyer);
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT auction_test_failure CHECK(type<>'AUCTION_OUTBID') NOT VALID");
        try { assertThatThrownBy(()->auctions.bid(id,bid(1100),other)).isInstanceOf(DataAccessException.class); }
        finally { jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT auction_test_failure"); }
        assertThat(auctions.get(id,buyer).highestAmount()).isEqualTo(1000);
        assertThat(auctions.get(id,buyer).bidCount()).isEqualTo(1);
        ReflectionTestUtils.setField(auctions,"enabled",false);
        assertThatThrownBy(()->auctions.create(request("PLN",1),seller)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(()->auctions.bid(id,bid(1100),other)).isInstanceOf(ForbiddenOperationException.class);
        assertThat(auctions.get(id,null)).isNotNull();
    }

    @Test void ordinarySaleDeclarationUpdatesAuctionFulfillmentWithoutConfirmingReputation() {
        long id=create(); auctions.bid(id,bid(1000),buyer); expire(id); closing.closeDue();
        fulfillment.start(id,buyer); listings.markAsSold(id,seller);
        assertThat(auctions.get(id,seller).fulfillmentStatus()).isEqualTo("DECLARED_COMPLETED");
        assertThat(profiles.get(seller.getId()).confirmedSales()).isZero();
    }

    @Test void multipleClosersAndReplayProduceOneResultAndOneSetOfNotifications() throws Exception {
        long id=create(); auctions.bid(id,bid(1000),buyer); expire(id);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(()->closing.closeDue()); var second=pool.submit(()->closing.closeDue());
            first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        }
        closing.closeDue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_fulfillments WHERE auction_id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE auction_id=? AND type IN ('AUCTION_ENDED','AUCTION_WON')",Long.class,id)).isEqualTo(2);
    }

    @Test void apiRejectsFractionalUnitsMalformedKeysAndDisabledPln() throws Exception {
        long id=create();
        mvc.perform(post("/api/auctions/"+id+"/bids").with(user(buyer)).contentType("application/json")
                .content("{\"amount\":1000.5,\"currency\":\"PLN\",\"idempotencyKey\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auctions/"+id+"/bids").with(user(buyer)).contentType("application/json")
                .content("{\"amount\":1000,\"currency\":\"PLN\",\"idempotencyKey\":\"bad-key\"}"))
                .andExpect(status().isBadRequest());
        ReflectionTestUtils.setField(auctions,"plnEnabled",false);
        mvc.perform(post("/api/auctions/"+id+"/bids").with(user(buyer)).contentType("application/json")
                .content("{\"amount\":1000,\"currency\":\"PLN\",\"idempotencyKey\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isForbidden());
    }
}
