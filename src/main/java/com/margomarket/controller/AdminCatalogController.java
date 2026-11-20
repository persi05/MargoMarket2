package com.margomarket.controller;

import com.margomarket.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class AdminCatalogController {
    private final CatalogStore store;
    private final CatalogRefreshService refresh;

    @GetMapping
    public CatalogStore.Status status() {
        return store.status();
    }

    @PostMapping
    public ResponseEntity<CatalogStore.Status> start() {
        return ResponseEntity.accepted().body(refresh.start());
    }
}
