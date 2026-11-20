package com.margomarket.migration;

import org.flywaydb.core.Flyway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class MigrationTestSupport {
    protected JdbcTemplate jdbc;

    protected void migrate(PostgreSQLContainer database) {
        var dataSource = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    protected long createUser() {
        String name = "migration_" + UUID.randomUUID().toString().substring(0, 20);
        return jdbc.queryForObject("""
                INSERT INTO users(email,username,display_name,password,role_id)
                VALUES (?,?,?,'unused',(SELECT id FROM roles WHERE name='user')) RETURNING id
                """, Long.class, name + "@example.invalid", name, name);
    }

    protected long createGoldListing(long ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO listings(user_id,item_name,currency_id,server_id,contact,status_id,
                    listing_kind,gold_amount,price_minor,gold_rate_mode,rate_gold_units,rate_price_minor)
                VALUES (?,'Gold migration fixture',(SELECT id FROM currencies WHERE lower(name)='pln'),
                    (SELECT id FROM servers ORDER BY id LIMIT 1),'',
                    (SELECT id FROM listing_statuses WHERE name='active'),
                    'GOLD',1500000000,3450,'PLN_PER_G',1000000000,2300) RETURNING id
                """, Long.class, ownerId);
    }

    protected long createItemListing(long ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO listings(user_id,item_name,item_type_id,level,rarity_id,price,currency_id,
                    server_id,contact,status_id)
                VALUES (?,'Profile migration fixture',(SELECT id FROM item_types ORDER BY id LIMIT 1),100,
                    (SELECT id FROM rarities ORDER BY id LIMIT 1),100,
                    (SELECT id FROM currencies WHERE lower(name)='pln'),
                    (SELECT id FROM servers ORDER BY id LIMIT 1),'',
                    (SELECT id FROM listing_statuses WHERE name='active')) RETURNING id
                """, Long.class, ownerId);
    }

    protected void assertSqlState(String expectedState, Runnable statement) {
        var error = assertThrows(DataIntegrityViolationException.class, statement::run);
        assertThat(error.getMostSpecificCause()).isInstanceOf(SQLException.class);
        assertThat(((SQLException) error.getMostSpecificCause()).getSQLState()).isEqualTo(expectedState);
    }
}
