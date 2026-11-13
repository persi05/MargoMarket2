package com.margomarket.controller;

import com.margomarket.dto.*;
import com.margomarket.model.User;
import com.margomarket.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {
    private final AuctionService service;
    private final AuctionFulfillmentService fulfillment;
    public record Reason(@NotBlank @Size(max=1000) String reason) {}
    public record Action(@NotBlank String action,@Size(max=1000) String reason) {}
    @GetMapping("/config") public Map<String,Object> config() { return service.config(); }
    @GetMapping public PageResponse<AuctionResponse> list(@RequestParam(required=false) Long serverId,
            @RequestParam(required=false) String currency,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="ACTIVE") String scope,@AuthenticationPrincipal User user) {
        return service.search(serverId,currency,search,page,scope,user);
    }
    @GetMapping("/mine") public PageResponse<AuctionResponse> mine(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="SELLING") String scope,@AuthenticationPrincipal User user) {
        return service.search(null,null,"",page,scope,user);
    }
    @GetMapping("/{id}") public AuctionResponse get(@PathVariable long id,@AuthenticationPrincipal User user) { return service.get(id,user); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AuctionResponse create(@Valid @RequestBody AuctionRequest r,@AuthenticationPrincipal User user) { return service.create(r,user); }
    @PutMapping("/{id}") public AuctionResponse edit(@PathVariable long id,@Valid @RequestBody AuctionRequest r,@AuthenticationPrincipal User user) { return service.update(id,r,user); }
    @GetMapping("/{id}/bids") public PageResponse<Map<String,Object>> bids(@PathVariable long id,@RequestParam(defaultValue="1") int page,@AuthenticationPrincipal User user) { return service.bids(id,page,user); }
    @PostMapping("/{id}/bids") public AuctionService.Receipt bid(@PathVariable long id,@Valid @RequestBody PlaceBidRequest r,@AuthenticationPrincipal User user) { return service.bid(id,r,user); }
    @PostMapping("/{id}/cancel") public void cancel(@PathVariable long id,@Valid @RequestBody Reason r,@AuthenticationPrincipal User user) { service.cancel(id,user,r.reason()); }
    @PostMapping("/{id}/reports") public void report(@PathVariable long id,@Valid @RequestBody Reason r,@AuthenticationPrincipal User user) { service.report(id,user,r.reason()); }
    @PostMapping("/{id}/fulfillment/start") public Map<String,Long> start(@PathVariable long id,@AuthenticationPrincipal User user) { return Map.of("conversationId",fulfillment.start(id,user)); }
    @PostMapping("/{id}/fulfillment/actions") public void action(@PathVariable long id,@Valid @RequestBody Action r,@AuthenticationPrincipal User user) { fulfillment.action(id,user,r.action(),r.reason()); }
}
