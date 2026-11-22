package com.margomarket.controller;

import com.margomarket.dto.SellerReviewDtos.*;
import com.margomarket.model.User;
import com.margomarket.service.SellerReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SellerReviewController {
    private final SellerReviewService service;

    @GetMapping("/api/profiles/{id}/reviews")
    public SellerReviewsResponse forSeller(@PathVariable Long id, @RequestParam(defaultValue = "1") int page) {
        return service.forSeller(id, page);
    }

    @GetMapping("/api/reviews/pending")
    public List<PendingReview> pending(@AuthenticationPrincipal User user) {
        return service.pending(user);
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerReviewResponse create(@AuthenticationPrincipal User user, @RequestBody CreateReviewRequest request) {
        return service.create(user, request);
    }

    @PostMapping("/api/admin/reviews/{id}/hide")
    public void hide(@PathVariable Long id, @AuthenticationPrincipal User user, @RequestBody HideReviewRequest request) {
        service.hide(id, user, request.reason());
    }
}
