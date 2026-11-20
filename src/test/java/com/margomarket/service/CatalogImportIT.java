package com.margomarket.service;

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
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.*;

@SpringBootTest
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class CatalogImportIT {
    @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data","rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired CatalogStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @MockBean CatalogSource source;

    @BeforeEach
    void reset() {
        jdbc.update(
                "UPDATE catalog_refresh SET"
                    + " initialized=false,status='IDLE',job_id=NULL,heartbeat_at=NULL,error=NULL"
                    + " WHERE id=1");
    }

    CatalogItem item(long id, String name) {
        return new CatalogItem(
                id,
                name,
                "https://micc.garmory-cdn.cloud/obrazki/itemy/test.gif",
                100,
                "Jednoręczne",
                "Legendarny",
                null,
                "rarity=legendary;lvl=100;binds",
                "margoworld",
                "https://margoworld.pl/item/view/" + id + "/test");
    }

    @Test
    void preservesIdentityUpdatesExistingAndKeepsMissingItems() {
        long id = 900000001L;
        store.initialize(List.of(item(id, "Przed aktualizacją"), item(id + 1, "Zachowany")));
        Long local =
                jdbc.queryForObject("SELECT id FROM items WHERE external_id=?", Long.class, id);
        UUID job = UUID.randomUUID();
        assertThat(store.claim(job)).isTrue();
        store.complete(job, List.of(item(id, "Po aktualizacji"), item(id + 2, "Nowy")));
        assertThat(jdbc.queryForObject("SELECT id FROM items WHERE external_id=?", Long.class, id))
                .isEqualTo(local);
        assertThat(jdbc.queryForObject("SELECT name FROM items WHERE id=?", String.class, local))
                .isEqualTo("Po aktualizacji");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT market_enabled FROM items WHERE external_id=?",
                                Boolean.class,
                                id + 1))
                .isTrue();
        assertThat(store.status().added()).isEqualTo(1);
        assertThat(store.status().updated()).isEqualTo(1);
        store.initialize(List.of(item(id, "Stary plik")));
        assertThat(jdbc.queryForObject("SELECT name FROM items WHERE id=?", String.class, local))
                .isEqualTo("Po aktualizacji");
    }

    @Test
    void invalidCatalogRollsBackAllChanges() {
        long id = 900000010L;
        store.initialize(List.of(item(id, "Oryginalny")));
        UUID job = UUID.randomUUID();
        store.claim(job);
        assertThatThrownBy(
                        () ->
                                store.complete(
                                        job, List.of(item(id, "Nadpisany"), item(id, "Duplikat"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM items WHERE external_id=?", String.class, id))
                .isEqualTo("Oryginalny");
        store.fail(job);
        assertThat(store.status().status()).isEqualTo("FAILED");
    }

    @Test
    void importsAreIdempotent() {
        var catalog = List.of(item(900000020L, "Stały"));
        store.initialize(catalog);
        UUID job = UUID.randomUUID();
        store.claim(job);
        store.complete(job, catalog);
        assertThat(store.status().added()).isZero();
        assertThat(store.status().updated()).isZero();
    }

    @Test
    void onlyOneJobOwnsLeaseAndOldWorkerCannotCommit() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertThat(store.claim(first)).isTrue();
        assertThat(store.claim(second)).isFalse();
        jdbc.update(
                "UPDATE catalog_refresh SET heartbeat_at=clock_timestamp()-interval '6 minutes'"
                        + " WHERE id=1");
        assertThat(store.status().status()).isEqualTo("FAILED");
        assertThat(store.claim(second)).isTrue();
        assertThatThrownBy(() -> store.complete(first, List.of(item(900000030L, "Stary worker"))))
                .isInstanceOf(IllegalStateException.class);
        store.fail(first);
        assertThat(store.status().jobId()).isEqualTo(second);
        store.fail(second);
    }

    @Test
    void failedBackgroundFetchPreservesCatalog() throws Exception {
        long id = 900000040L;
        store.initialize(List.of(item(id, "Zachowany po błędzie")));
        org.mockito.Mockito.when(source.fetch(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("Unavailable source"));
        mvc.perform(post("/api/admin/catalog").with(user("admin").roles("ADMIN")))
                .andExpect(status().isAccepted());
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> "FAILED".equals(store.status().status()));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM items WHERE external_id=?", String.class, id))
                .isEqualTo("Zachowany po błędzie");
    }

    @Test
    void catalogEndpointsRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/catalog").with(user("regular").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalog").with(user("regular").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/catalog").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    @Test
    void successfulBackgroundImportBecomesSearchable() throws Exception {
        long id = 900000050L;
        org.mockito.Mockito.when(source.fetch(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(item(id, "Nowy przedmiot z aktualizacji")));
        mvc.perform(post("/api/admin/catalog").with(user("admin").roles("ADMIN")))
                .andExpect(status().isAccepted());
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .until(() -> "SUCCEEDED".equals(store.status().status()));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT market_enabled FROM items WHERE external_id=?",
                                Boolean.class,
                                id))
                .isTrue();
        assertThat(store.status().added()).isEqualTo(1);
        mvc.perform(get("/api/items").param("search", "Nowy przedmiot z aktualizacji"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nowy przedmiot z aktualizacji"));
    }
}
