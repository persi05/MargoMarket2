package com.margomarket.controller;

import com.margomarket.dto.*;
import com.margomarket.mapper.*;
import com.margomarket.model.User;
import com.margomarket.service.GoldListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/listings")
public class GoldListingController {

    private final GoldListingService service;
    private final ListingMapper mapper;
    private final PageMapper pages;

    @GetMapping("/gold")
    public PageResponse<ListingResponse> search(
        @Valid @ModelAttribute GoldListingFilter filter,
        @RequestParam(defaultValue = "false") boolean publicOnly,
        @AuthenticationPrincipal User user
    ) {
        if (
            user == null &&
            (filter.getMinPrice() != null ||
                filter.getMaxPrice() != null ||
                "priceAsc".equals(filter.getSort()) ||
                "priceDesc".equals(filter.getSort()))
        ) throw new com.margomarket.exception.ForbiddenOperationException(
            "Zaloguj się, aby filtrować i sortować po cenie"
        );
        return pages.toResponse(service.search(filter), l ->
            mapper.toResponse(l, !publicOnly && user != null)
        );
    }

    @GetMapping("/gold/config")
    public java.util.Map<String, Boolean> config() {
        return java.util.Map.of("enabled", service.enabled());
    }

    @PostMapping("/gold")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ListingResponse create(
        @Valid @RequestBody GoldListingRequest request,
        @AuthenticationPrincipal User user
    ) {
        return mapper.toResponse(service.create(request, user));
    }

    @PutMapping("/{id}/gold")
    public ListingResponse update(
        @PathVariable Long id,
        @Valid @RequestBody GoldListingRequest request,
        @AuthenticationPrincipal User user
    ) {
        return mapper.toResponse(service.update(id, request, user));
    }

    @PostMapping("/{id}/withdraw")
    public ListingResponse withdraw(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return mapper.toResponse(service.withdraw(id, user));
    }
}
