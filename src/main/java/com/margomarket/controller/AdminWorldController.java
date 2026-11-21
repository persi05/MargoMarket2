package com.margomarket.controller;

import com.margomarket.dto.WorldRatesRequest;
import com.margomarket.dto.WorldResponse;
import com.margomarket.pricing.WorldAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
