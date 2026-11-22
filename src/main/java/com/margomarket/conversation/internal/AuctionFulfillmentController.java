package com.margomarket.conversation.internal;

import com.margomarket.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Settling a finished auction: the winner and the seller meet in a conversation. */
@Tag(name = "Aukcje", description = "Aukcje przedmiotów, licytacje i rozliczenia")
@RestController
@RequestMapping("/api/auctions/{id}/fulfillment")
@RequiredArgsConstructor
public class AuctionFulfillmentController {
    private final AuctionFulfillmentService fulfillment;

    public record Action(@NotBlank String action, @Size(max = 1000) String reason) {}

    @PostMapping("/start")
    public Map<String, Long> start(@PathVariable long id, @AuthenticationPrincipal User user) {
        return Map.of("conversationId", fulfillment.start(id, user));
    }

    @PostMapping("/actions")
    public void action(@PathVariable long id, @Valid @RequestBody Action r, @AuthenticationPrincipal User user) {
        fulfillment.action(id, user, r.action(), r.reason());
    }
}
