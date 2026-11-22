package com.margomarket.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Administracja: statystyki", description = "Statystyki serwisu")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {
    private final JdbcTemplate jdbc;

    public record Stats(long listings, long users) {}

    @GetMapping
    public Stats stats() {
        return jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM listings) AS listings, (SELECT count(*) FROM users)"
                    + " AS users",
                (row, index) -> new Stats(row.getLong("listings"), row.getLong("users")));
    }
}
