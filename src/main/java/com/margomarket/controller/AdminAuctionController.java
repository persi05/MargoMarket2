package com.margomarket.controller;

import com.margomarket.dto.PageResponse;
import com.margomarket.model.User;
import com.margomarket.service.AuctionModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {
    private final AuctionModerationService moderation;
    public record Restriction(@NotBlank @Size(max=1000) String reason,@NotBlank @Size(max=1000) String appealInstructions,@NotNull Instant expiresAt) {}
    @PostMapping("/{id}/bids/{bidId}/invalidate") public void invalidate(@PathVariable long id,@PathVariable long bidId,@Valid @RequestBody AuctionController.Reason r,@AuthenticationPrincipal User user) { moderation.invalidate(id,bidId,user,r.reason()); }
    @GetMapping("/register/{kind}") public PageResponse<Map<String,Object>> page(@PathVariable String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="OPEN") String state,@AuthenticationPrincipal User user) { return moderation.page(kind,page,state,user); }
    @PostMapping("/users/{id}/restrictions") public void restrict(@PathVariable long id,@Valid @RequestBody Restriction r,@AuthenticationPrincipal User user) { moderation.restrict(id,user,r.reason(),r.appealInstructions(),r.expiresAt()); }
    @PostMapping("/restrictions/{id}/revoke") public void revoke(@PathVariable long id,@Valid @RequestBody AuctionController.Reason r,@AuthenticationPrincipal User user) { moderation.revoke(id,user,r.reason()); }
    @PostMapping("/reports/{id}/resolve") public void resolve(@PathVariable long id,@Valid @RequestBody AuctionController.Reason r,@AuthenticationPrincipal User user) { moderation.resolveReport(id,user,r.reason()); }
    public record Review(@NotBlank @Size(max=1000) String reason,boolean invalidateBid) {}
    @PostMapping("/reports/{id}/review") public void review(@PathVariable long id,@Valid @RequestBody Review r,@AuthenticationPrincipal User user) { moderation.reviewReport(id,user,r.reason(),r.invalidateBid()); }

}
