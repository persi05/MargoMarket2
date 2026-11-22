package com.margomarket.shared;

import com.margomarket.catalog.internal.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the OpenAPI specification from the running application and writes it to docs/openapi.yaml.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class OpenApiSpecIT {
    static final Path SPEC_FILE = Path.of("docs", "openapi.yaml");

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
    @Autowired MockMvc mvc;

    @Test
    void publishesSpecificationWithoutTokenAndWritesItToDocs() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("title: MargoMarket API")
                .contains("bearerAuth")
                .contains("/api/auth/login:")
                .contains("/api/auctions/{id}:")
                .contains("/api/admin/users:")
                .contains("ApiError:")
                .contains("name: Aukcje")
                .doesNotContain("  /ws/conversations:");

        Files.createDirectories(SPEC_FILE.getParent());
        Files.writeString(SPEC_FILE, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void splitsUserAndAdminGroups() throws Exception {
        String user = mvc.perform(get("/v3/api-docs/uzytkownik")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String admin = mvc.perform(get("/v3/api-docs/administracja")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(user).contains("\"/api/listings\":").doesNotContain("\"/api/admin/users\":");
        assertThat(admin).contains("\"/api/admin/users\":").doesNotContain("\"/api/listings\":");
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
