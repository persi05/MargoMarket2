package com.margomarket.ratelimit;

import com.margomarket.catalog.ItemCatalogImporter;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.security.JwtService;
import com.margomarket.user.User;
import com.margomarket.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.limits.chat-message.capacity=1",
        "app.rate-limit.limits.chat-message.period=1m"})
@ActiveProfiles("auction-test")
@AutoConfigureMockMvc
@Testcontainers
class RateLimitIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;

    private User account(String password) {
        String name = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject(
                "INSERT INTO users(email,username,display_name,password,role_id,email_verified,created_at)"
                        + " VALUES (?,?,?,?,(SELECT id FROM roles WHERE name='user'),true,localtimestamp-interval '2 days')"
                        + " RETURNING id",
                Long.class, name + "@example.invalid", name, name, passwords.encode(password));
        return users.findById(id).orElseThrow();
    }

    private String login(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void repeatedFailedLoginsBlockEvenTheCorrectPassword() throws Exception {
        User user = account("poprawne-haslo");
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(login(user.getUsername(), "zle-haslo")))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(user.getUsername(), "poprawne-haslo")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void successfulLoginIsNotBlockedByEarlierFailures() throws Exception {
        User user = account("poprawne-haslo");
        for (int i = 0; i < 4; i++)
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(login(user.getUsername(), "zle-haslo")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(user.getUsername(), "poprawne-haslo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void perUserLimitAppliesToAuthenticatedRequestsInTheSecurityChain() throws Exception {
        String token = "Bearer " + jwt.generateToken(account("haslo123"));
        var message = post("/api/conversations/999999/messages").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Cześć\"}");

        mvc.perform(message).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        mvc.perform(message)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Zbyt wiele prób.")));
    }
}
