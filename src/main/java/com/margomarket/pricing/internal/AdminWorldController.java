package com.margomarket.pricing.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Administracja: światy", description = "Zarządzanie światami gry")
@RestController
@RequestMapping("/api/admin/worlds")
@RequiredArgsConstructor
public class AdminWorldController {
    private final WorldAdminService worlds;

    @GetMapping
    public List<WorldResponse> list() {
        return worlds.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse create(@Valid @RequestBody WorldRatesRequest request) {
        return worlds.create(request);
    }

    @PutMapping("/{id}")
    public WorldResponse update(@PathVariable long id, @Valid @RequestBody WorldRatesRequest request) {
        return worlds.update(id, request);
    }
}
