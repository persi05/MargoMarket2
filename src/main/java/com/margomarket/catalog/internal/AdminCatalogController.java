package com.margomarket.catalog.internal;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Administracja: katalog", description = "Odświeżanie katalogu przedmiotów")
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
