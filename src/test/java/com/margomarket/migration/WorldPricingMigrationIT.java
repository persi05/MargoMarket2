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
class WorldPricingMigrationIT extends MigrationTestSupport {
    @Container
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(Duration.ofMinutes(3));

    @BeforeAll void migrateDatabase() { migrate(DATABASE); }

    private Map<String, Object> world(String name) {
        return jdbc.queryForMap("SELECT world_type,gold_per_pln,upgrade_million_price_grosze,essence_price_grosze FROM servers WHERE name=?", name);
    }

    @Test void addsMissingWorlds() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servers WHERE name IN ('Luvia','Nerthus')", Long.class)).isEqualTo(2L);
    }

    @Test void groupsWorldsByTransferRules() {
        assertThat(jdbc.queryForList("SELECT name FROM servers WHERE world_type='ISOLATED' ORDER BY name", String.class))
                .containsExactly("Aether", "Aldous", "Berufs", "Brutal", "Fobos", "Gordion", "Luvia", "Nerthus", "Nomada", "Perkun", "Tempest");
        assertThat(jdbc.queryForList("SELECT name FROM servers WHERE world_type='PRIVATE' ORDER BY name", String.class))
                .containsExactly("Pandora", "Syberia", "Unia");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servers WHERE world_type='TRANSFERABLE'", Long.class)).isEqualTo(11L);
    }

    @Test void storesRatesInMinorUnits() {
        assertThat(world("Luvia")).containsEntry("gold_per_pln", 18_000_000L)
                .containsEntry("upgrade_million_price_grosze", 15_000L).containsEntry("essence_price_grosze", 200L);
        assertThat(world("Pandora")).containsEntry("gold_per_pln", 50_000_000L);
        assertThat(world("Perkun")).containsEntry("gold_per_pln", 20_000_000L)
                .containsEntry("upgrade_million_price_grosze", null);
        assertThat(world("Nerthus")).containsEntry("gold_per_pln", null);
    }
}
