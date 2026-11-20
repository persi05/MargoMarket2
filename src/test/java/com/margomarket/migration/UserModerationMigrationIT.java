package com.margomarket.migration;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class UserModerationMigrationIT extends MigrationTestSupport {
    @Container
    static final PostgreSQLContainer DB =
            new PostgreSQLContainer("postgres:17-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data","rw"))
                    .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @Test
    void upgradesExistingAuctionRestrictionsWithoutLosingReasonsOrRevocations() {
        var source =
                new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("31").load().migrate();
        jdbc = new JdbcTemplate(source);
        long admin = createUser(), user = createUser();
        long legacy =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " auction_user_restrictions(user_id,actor_id,reason,appeal_instructions,expires_at)"
                            + " VALUES (?,?,'Stare ograniczenie','Kontakt z"
                            + " administracją',clock_timestamp()+interval '1 day') RETURNING id",
                        Long.class,
                        user,
                        admin);
        jdbc.update(
                "INSERT INTO"
                    + " auction_user_restrictions(user_id,actor_id,reason,appeal_instructions,expires_at,revoked_at,revoked_by,revoke_reason)"
                    + " VALUES (?,?,'Cofnięte ograniczenie','Kontakt',clock_timestamp()+interval '1"
                    + " day',clock_timestamp(),?,'Wyjaśniono')",
                user,
                admin,
                admin);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT scope FROM user_moderation_actions WHERE"
                                    + " legacy_auction_restriction_id=?",
                                String.class,
                                legacy))
                .isEqualTo("BIDDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reason FROM user_moderation_actions WHERE"
                                    + " legacy_auction_restriction_id=?",
                                String.class,
                                legacy))
                .contains("Stare ograniczenie", "Kontakt z administracją");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_moderation_actions WHERE user_id=? AND"
                                    + " revoked_at IS NOT NULL",
                                Long.class,
                                user))
                .isEqualTo(1);
    }
}
