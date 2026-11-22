package com.margomarket.pricing;

import com.margomarket.shared.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Price suggestions and history; like listing prices, visible only to signed-in users. */
@Tag(name = "Ceny", description = "Historia i sugestie cen przedmiotów")
@RestController
@RequestMapping("/api/prices/items/{itemId}")
@RequiredArgsConstructor
public class PriceController {
    private final PriceSuggestionService prices;

    @GetMapping
    public PriceSuggestionService.Overview overview(@PathVariable long itemId, @RequestParam long serverId,
                                                    @RequestParam(defaultValue = "false") boolean bound) {
        return prices.overview(itemId, serverId, bound);
    }

    @GetMapping("/history")
    public PageResponse<PriceSuggestionService.HistoryEntry> history(@PathVariable long itemId,
                                                                     @RequestParam(required = false) Long serverId,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return prices.history(itemId, serverId, page, size);
    }
}
