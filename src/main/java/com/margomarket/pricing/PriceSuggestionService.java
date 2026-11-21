package com.margomarket.pricing;

import com.margomarket.dto.PageResponse;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Item;
import com.margomarket.model.Server;
import com.margomarket.repository.ItemRepository;
import com.margomarket.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceSuggestionService {
    public static final int MAX_HISTORY_PAGE_SIZE = 50;

    private final ItemRepository items;
    private final ServerRepository servers;
    private final PriceSampleRepository samples;

    public record Overview(long itemId, String itemName, String rarity, int level, long serverId, String serverName,
                           String worldType, Long goldPerPln, boolean recentOnly, int recentDays,
                           List<String> marketWorlds, List<PriceSuggestionCalculator.Level> levels) {}

    public record HistoryEntry(long listingId, PriceSample.Source source, String serverName, int enhancement,
                               boolean bound, PriceSample.Currency currency, long amount, Long plnGrosze, Instant at) {}

    @Transactional(readOnly = true)
    public Overview overview(long itemId, long serverId, boolean bound) {
        Item item = item(itemId);
        Map<Long, Server> worlds = worlds();
        Server target = worlds.get(serverId);
        if (target == null) throw new NotFoundException("Nie znaleziono świata");
        var result = PriceSuggestionCalculator.suggest(item.getRarity().getName(), item.getLevel(), target, worlds,
                samples.findByItem(itemId), bound, Instant.now());
        List<String> market = result.marketWorldIds().stream().map(id -> worlds.get(id).getName()).sorted().toList();
        return new Overview(item.getId(), item.getName(), item.getRarity().getName(), item.getLevel(), target.getId(),
                target.getName(), target.getWorldType().name(), target.getGoldPerPln(), result.recentOnly(),
                (int) PriceSuggestionCalculator.RECENT_WINDOW.toDays(), market, result.levels());
    }

    /** Observed prices, newest first; limited to the world's market when {@code serverId} is given. */
    @Transactional(readOnly = true)
    public PageResponse<HistoryEntry> history(long itemId, Long serverId, int page, int size) {
        item(itemId);
        Map<Long, Server> worlds = worlds();
        List<Long> market;
        if (serverId == null) market = null;
        else {
            Server target = worlds.get(serverId);
            if (target == null) throw new NotFoundException("Nie znaleziono świata");
            market = PriceSuggestionCalculator.marketWorldIds(target, worlds);
        }
        List<HistoryEntry> entries = samples.findByItem(itemId).stream()
                .filter(s -> market == null || market.contains(s.serverId()))
                .sorted(Comparator.comparing(PriceSample::at).reversed())
                .map(s -> entry(s, worlds.get(s.serverId())))
                .toList();
        int pageSize = Math.min(Math.max(1, size), MAX_HISTORY_PAGE_SIZE);
        int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int current = Math.min(Math.max(1, page), totalPages);
        List<HistoryEntry> content = entries.subList((current - 1) * pageSize, Math.min(entries.size(), current * pageSize));
        return new PageResponse<>(content, current, pageSize, entries.size(), totalPages, current == 1, current == totalPages);
    }

    private static HistoryEntry entry(PriceSample sample, Server world) {
        OptionalLong grosze = sample.currency() == PriceSample.Currency.PLN
                ? OptionalLong.of(sample.amount()) : WorldRates.goldToGrosze(sample.amount(), world);
        return new HistoryEntry(sample.listingId(), sample.source(), world.getName(), sample.enhancement(), sample.bound(),
                sample.currency(), sample.amount(), grosze.isPresent() ? grosze.getAsLong() : null, sample.at());
    }

    private Item item(long itemId) {
        return items.findById(itemId).orElseThrow(() -> new NotFoundException("Nie znaleziono przedmiotu"));
    }

    private Map<Long, Server> worlds() {
        return servers.findAll().stream().collect(Collectors.toMap(Server::getId, Function.identity()));
    }
}
