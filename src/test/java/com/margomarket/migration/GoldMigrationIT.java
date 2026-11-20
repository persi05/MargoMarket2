package com.margomarket.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoldMigrationIT extends MigrationTestSupport {
    @Container
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data","rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));
    private long listingId;

    @BeforeAll void migrateDatabase() { migrate(DATABASE); }
    @BeforeEach void createListing() { listingId = createGoldListing(createUser()); }

    @Test void activeGoldPackageAppearsInListingView() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM active_listings_view WHERE id=?", Long.class, listingId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT price_minor FROM listings WHERE id=?", Long.class, listingId))
                .isEqualTo(3450L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 9000000000000001L})
    void databaseRejectsGoldAmountsOutsideAllowedRange(long amount) {
        assertSqlState("23514", () -> jdbc.update("UPDATE listings SET gold_amount=? WHERE id=?", amount, listingId));
        assertThat(jdbc.queryForObject("SELECT gold_amount FROM listings WHERE id=?", Long.class, listingId))
                .isEqualTo(1500000000L);
    }

    @Test void goldPackageCannotContainItemLevel() {
        assertSqlState("23514", () -> jdbc.update("UPDATE listings SET level=10 WHERE id=?", listingId));
    }

    @Test void databaseRejectsZeroPackagePrice() {
        assertSqlState("23514", () -> jdbc.update("UPDATE listings SET price_minor=0 WHERE id=?", listingId));
    }
}
