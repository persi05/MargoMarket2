package com.margomarket.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.margomarket.dto.PageResponse;
import com.margomarket.model.User;
import com.margomarket.service.AuctionModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name = "Administracja: aukcje", description = "Unieważnianie ofert, zgłoszenia i ograniczenia aukcyjne")
@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {
    private final AuctionModerationService moderation;
    @PostMapping("/{id}/bids/{bidId}/invalidate") public void invalidate(@PathVariable long id,@PathVariable long bidId,@Valid @RequestBody AuctionController.Reason r,@AuthenticationPrincipal User user) { moderation.invalidate(id,bidId,user,r.reason()); }
    @GetMapping("/register/{kind}") public PageResponse<Map<String,Object>> page(@PathVariable String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="OPEN") String state,@AuthenticationPrincipal User user) { return moderation.page(kind,page,state,user); }
    @PostMapping("/restrictions/{id}/revoke") public void revoke(@PathVariable long id,@Valid @RequestBody AuctionController.Reason r,@AuthenticationPrincipal User user) { moderation.revoke(id,user,r.reason()); }
    public record Review(@NotBlank @Size(max=1000) String reason,boolean invalidateBid) {}
    @PostMapping("/reports/{id}/review") public void review(@PathVariable long id,@Valid @RequestBody Review r,@AuthenticationPrincipal User user) { moderation.reviewReport(id,user,r.reason(),r.invalidateBid()); }

}
