package com.margomarket.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.margomarket.model.User;
import com.margomarket.service.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

@Tag(name = "Moderacja użytkowników", description = "Ograniczenia kont i ich historia")
@RestController
@RequiredArgsConstructor
public class UserModerationController {
    private final UserModerationService moderation;

    public record Restriction(
            @NotEmpty Set<ModerationScope> scopes,
            @NotBlank @Size(max = 1000) String reason,
            Long durationSeconds,
            Instant expiresAt,
            boolean permanent) {}

    public record Reason(@NotBlank @Size(max = 1000) String reason) {}

    @GetMapping("/api/admin/users/{id}/moderation")
    public UserModerationService.Overview overview(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal User actor) {
        return moderation.overview(id, page, actor);
    }

    @PostMapping("/api/admin/users/{id}/moderation/restrictions")
    public void restrict(
            @PathVariable long id,
            @Valid @RequestBody Restriction request,
            @AuthenticationPrincipal User actor) {
        moderation.restrict(
                id,
                request.scopes(),
                request.reason(),
                new UserModerationService.Term(
                        request.durationSeconds(), request.expiresAt(), request.permanent()),
                actor);
    }

    @PostMapping("/api/admin/users/{id}/moderation/warnings")
    public void warn(
            @PathVariable long id,
            @Valid @RequestBody Reason request,
            @AuthenticationPrincipal User actor) {
        moderation.warn(id, request.reason(), actor);
    }

    @PostMapping("/api/admin/users/{id}/moderation/restrictions/{actionId}/revoke")
    public void revoke(
            @PathVariable long id,
            @PathVariable long actionId,
            @Valid @RequestBody Reason request,
            @AuthenticationPrincipal User actor) {
        moderation.revoke(id, actionId, request.reason(), actor);
    }

    @GetMapping("/api/auth/moderation")
    public UserModerationService.Status status(@AuthenticationPrincipal User user) {
        return moderation.status(user.getId());
    }
}
