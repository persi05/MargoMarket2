package com.margomarket.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPriceMigrationIT extends MigrationTestSupport {
    @Container
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll void migrateDatabase() { migrate(DATABASE); }

    @Test void storesGoldPricesAboveTheIntegerRange() {
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='listings' AND column_name='price'", String.class))
                .isEqualTo("bigint");
        long id = createItemListing(createUser());
        jdbc.update("UPDATE listings SET price=6500000000, currency_id=(SELECT id FROM currencies WHERE lower(name)='w grze') WHERE id=?", id);
        assertThat(jdbc.queryForObject("SELECT price FROM active_listings_view WHERE id=?", Long.class, id)).isEqualTo(6_500_000_000L);
        assertThat(jdbc.queryForObject("SELECT price FROM search_listings() WHERE id=?", Long.class, id)).isEqualTo(6_500_000_000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_favorites_view", Long.class)).isNotNull();
    }
}
