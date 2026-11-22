package com.margomarket.conversation.internal;

import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Potwierdzenia wymian", description = "Potwierdzanie zakończonych transakcji")
@RestController
@RequiredArgsConstructor
public class ExchangeConfirmationController {
    private final ExchangeConfirmationService service;

    @GetMapping("/api/admin/exchange-confirmations")
    public PageResponse<java.util.Map<String, Object>> page(
            @AuthenticationPrincipal User user, @RequestParam(defaultValue = "1") int page) {
        return service.page(user, page);
    }

    public record RevokeRequest(String reason) {}

    @PostMapping("/api/conversations/{id}/exchange-confirmation")
    public ConversationResponse confirm(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.confirm(id, user);
    }

    @PostMapping("/api/admin/exchange-confirmations/{id}/revoke")
    public void revoke(@PathVariable Long id, @AuthenticationPrincipal User user, @RequestBody RevokeRequest request) {
        service.revoke(id, user, request.reason());
    }
}
