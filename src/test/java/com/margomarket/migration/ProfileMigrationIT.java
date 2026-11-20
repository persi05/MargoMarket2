package com.margomarket.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileMigrationIT extends MigrationTestSupport {
    @Container
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data","rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));
    private long buyerId;
    private long sellerId;
    private long confirmerId;
    private long listingId;
    private long conversationId;

    @BeforeAll void migrateDatabase() { migrate(DATABASE); }

    @BeforeEach void createExchange() {
        buyerId = createUser();
        sellerId = createUser();
        confirmerId = createUser();
        listingId = createItemListing(sellerId);
        conversationId = createConversation(listingId);
        confirm(conversationId);
    }

    @Test void listingCannotBeConfirmedTwiceInDifferentConversations() {
        long otherConversation = createConversation(null);
        assertSqlState("23505", () -> confirm(otherConversation));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE listing_id=?", Long.class, listingId))
                .isEqualTo(1L);
    }

    @Test void deletingBuyerPreservesSellersConfirmedExchange() {
        jdbc.update("DELETE FROM users WHERE id=?", buyerId);
        var confirmation = jdbc.queryForMap("SELECT buyer_id,seller_id,conversation_id FROM exchange_confirmations WHERE listing_id=?", listingId);
        assertThat(confirmation.get("buyer_id")).isNull();
        assertThat(confirmation.get("conversation_id")).isNull();
        assertThat(confirmation.get("seller_id")).isEqualTo(sellerId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE seller_id=? AND revoked_at IS NULL", Long.class, sellerId))
                .isEqualTo(1L);
    }

    private long createConversation(Long listing) {
        return jdbc.queryForObject("""
                INSERT INTO conversations(listing_id,item_name,buyer_id,seller_id)
                VALUES (?,'Profile migration fixture',?,?) RETURNING id
                """, Long.class, listing, buyerId, sellerId);
    }

    private void confirm(long conversation) {
        jdbc.update("""
                INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by)
                VALUES (?,?,?,?,?)
                """, conversation, listingId, buyerId, sellerId, confirmerId);
    }
}
