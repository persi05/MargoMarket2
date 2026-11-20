package com.margomarket.service;

import com.margomarket.model.*;
import com.margomarket.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogStore {
    private final JdbcTemplate jdbc;
    private final ItemRepository items;
    private final ItemTypeRepository types;
    private final RarityRepository rarities;

    public record Status(
            String status,
            UUID jobId,
            Instant startedAt,
            Instant finishedAt,
            int pages,
            int added,
            int updated,
            int total,
            String error) {}

    public Status status() {
        jdbc.update(
                "UPDATE catalog_refresh SET status='FAILED', finished_at=clock_timestamp(), error=?"
                    + " WHERE id=1 AND status='RUNNING' AND heartbeat_at <"
                    + " clock_timestamp()-interval '5 minutes'",
                "Aktualizacja została przerwana. Możesz spróbować ponownie.");
        return jdbc.queryForObject(
                "SELECT * FROM catalog_refresh WHERE id=1",
                (rs, n) ->
                        new Status(
                                rs.getString("status"),
                                rs.getObject("job_id", UUID.class),
                                rs.getTimestamp("started_at") == null
                                        ? null
                                        : rs.getTimestamp("started_at").toInstant(),
                                rs.getTimestamp("finished_at") == null
                                        ? null
                                        : rs.getTimestamp("finished_at").toInstant(),
                                rs.getInt("pages"),
                                rs.getInt("added"),
                                rs.getInt("updated"),
                                rs.getInt("total"),
                                rs.getString("error")));
    }

    @Transactional
    public boolean claim(UUID job) {
        status();
        return jdbc.update(
                        "UPDATE catalog_refresh SET"
                            + " status='RUNNING',job_id=?,started_at=clock_timestamp(),heartbeat_at=clock_timestamp(),finished_at=NULL,pages=0,added=0,updated=0,total=0,error=NULL"
                            + " WHERE id=1 AND status<>'RUNNING'",
                        job)
                == 1;
    }

    public void progress(UUID job, int pages) {
        if (jdbc.update(
                        "UPDATE catalog_refresh SET pages=?,heartbeat_at=clock_timestamp() WHERE"
                            + " id=1 AND status='RUNNING' AND job_id=? AND heartbeat_at >="
                            + " clock_timestamp()-interval '5 minutes'",
                        pages,
                        job)
                != 1) throw new IllegalStateException("Import lease expired");
    }

    public void fail(UUID job) {
        jdbc.update(
                "UPDATE catalog_refresh SET status='FAILED',finished_at=clock_timestamp(),error=?"
                    + " WHERE id=1 AND status='RUNNING' AND job_id=?",
                "Nie udało się zaktualizować katalogu. Dotychczasowe dane zostały zachowane."
                    + " Spróbuj ponownie później.",
                job);
    }

    public boolean initialized() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT initialized FROM catalog_refresh WHERE id=1", Boolean.class));
    }

    @Transactional
    public void initialize(List<CatalogItem> catalog) {
        boolean initialized =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT initialized FROM catalog_refresh WHERE id=1 FOR UPDATE",
                                Boolean.class));
        if (initialized) return;
        apply(catalog);
        // Only the initial bundled snapshot replaces the legacy catalog selection.
        jdbc.update("UPDATE items SET market_enabled=false WHERE source<>'margoworld'");
        jdbc.update("UPDATE catalog_refresh SET initialized=true WHERE id=1");
    }

    @Transactional
    public void complete(UUID job, List<CatalogItem> catalog) {
        jdbc.queryForObject("SELECT id FROM catalog_refresh WHERE id=1 FOR UPDATE", Integer.class);
        Status current = status();
        if (!"RUNNING".equals(current.status()) || !job.equals(current.jobId()))
            throw new IllegalStateException("Import no longer owns lease");
        int[] counts = apply(catalog);
        jdbc.update(
                "UPDATE catalog_refresh SET"
                    + " initialized=true,status='SUCCEEDED',finished_at=clock_timestamp(),added=?,updated=?,total=?"
                    + " WHERE id=1",
                counts[0],
                counts[1],
                catalog.size());
    }

    private int[] apply(List<CatalogItem> catalog) {
        if (catalog.isEmpty() || catalog.size() > 30000)
            throw new IllegalArgumentException("Invalid catalog size");
        var typeMap =
                types.findAll().stream()
                        .collect(Collectors.toMap(ItemType::getName, Function.identity()));
        var rarityMap =
                rarities.findAll().stream()
                        .collect(Collectors.toMap(Rarity::getName, Function.identity()));
        Set<Long> ids = new HashSet<>();
        for (var c : catalog) {
            if (c.externalId() == null
                    || c.externalId() <= 0
                    || !ids.add(c.externalId())
                    || c.name() == null
                    || c.name().isBlank()
                    || c.name().length() > 255
                    || c.level() == null
                    || c.level() < 1
                    || c.level() > 300
                    || c.iconUrl() == null
                    || !c.iconUrl().startsWith("https://micc.garmory-cdn.cloud/obrazki/itemy/")
                    || !typeMap.containsKey(c.itemTypeName())
                    || !rarityMap.containsKey(c.rarityName())
                    || !"margoworld".equals(c.source()))
                throw new IllegalArgumentException("Invalid catalog item");
        }
        var existing =
                items.findAll().stream()
                        .collect(Collectors.toMap(Item::getExternalId, Function.identity()));
        int added = 0, updated = 0;
        List<Item> changed = new ArrayList<>();
        for (var c : catalog) {
            Item item = existing.get(c.externalId());
            if (item == null) {
                item = new Item();
                added++;
            } else if (c.equals(
                            new CatalogItem(
                                    item.getExternalId(),
                                    item.getName(),
                                    item.getIconUrl(),
                                    item.getLevel(),
                                    item.getItemType().getName(),
                                    item.getRarity().getName(),
                                    item.getDescription(),
                                    item.getStats(),
                                    item.getSource(),
                                    item.getSourceUrl()))
                    && item.isMarketEnabled()) continue;
            else updated++;
            item.setExternalId(c.externalId());
            item.setName(c.name());
            item.setIconUrl(c.iconUrl());
            item.setLevel(c.level());
            item.setItemType(typeMap.get(c.itemTypeName()));
            item.setRarity(rarityMap.get(c.rarityName()));
            item.setDescription(c.description());
            item.setStats(c.stats());
            item.setSource("margoworld");
            item.setSourceUrl(c.sourceUrl());
            item.setMarketEnabled(true);
            changed.add(item);
        }
        items.saveAllAndFlush(changed);
        return new int[] {added, updated};
    }
}
