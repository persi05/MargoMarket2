package com.margomarket.listing.auction;

import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name = "Aukcje", description = "Aukcje przedmiotów, licytacje i rozliczenia")
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {
    private final AuctionService service;
    public record Reason(@NotBlank @Size(max=1000) String reason) {}
    @GetMapping("/config") public Map<String,Object> config() { return service.config(); }
    @GetMapping public PageResponse<AuctionResponse> list(@RequestParam(required=false) Long serverId,
            @RequestParam(required=false) String currency,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="ACTIVE") String scope,
            @RequestParam(required=false) Long itemTypeId,@RequestParam(required=false) Boolean bound,
            @RequestParam(required=false) Integer minLevel,@RequestParam(required=false) Integer maxLevel,@AuthenticationPrincipal User user) {
        return service.search(serverId,currency,search,page,scope,user,itemTypeId,bound,minLevel,maxLevel);
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
    @PostMapping("/{id}/bids/{bidId}/reports") public void reportBid(@PathVariable long id,@PathVariable long bidId,@Valid @RequestBody Reason r,@AuthenticationPrincipal User user) { service.reportBid(id,bidId,user,r.reason()); }
    @PostMapping("/{id}/reports") public void report(@PathVariable long id,@Valid @RequestBody Reason r,@AuthenticationPrincipal User user) { service.report(id,user,r.reason()); }
}
