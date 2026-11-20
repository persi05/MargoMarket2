package com.margomarket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CatalogSource {
    private static final String BASE = "https://margoworld.pl";
    private static final Map<Integer, String> TYPES =
            Map.ofEntries(
                    Map.entry(1, "Jednoręczne"),
                    Map.entry(2, "Dwuręczne"),
                    Map.entry(3, "Półtoraręczne"),
                    Map.entry(4, "Dystansowe"),
                    Map.entry(5, "Pomocnicze"),
                    Map.entry(6, "Różdżki"),
                    Map.entry(7, "Orby"),
                    Map.entry(8, "Zbroja"),
                    Map.entry(9, "Hełm"),
                    Map.entry(10, "Buty"),
                    Map.entry(11, "Rękawice"),
                    Map.entry(12, "Pierścień"),
                    Map.entry(13, "Naszyjnik"),
                    Map.entry(14, "Tarcza"),
                    Map.entry(22, "Talizmany"),
                    Map.entry(29, "Strzały"));
    private static final Set<String> BLOCKED =
            Set.of("permbound", "force_binding", "noauction", "soulbound");
    private final ObjectMapper mapper;

    record Page(int total, int rawCount, List<CatalogItem> items, Set<Long> ids) {}

    public List<CatalogItem> fetch(IntConsumer progress) throws Exception {
        var paths = new ArrayList<String>();
        TYPES.keySet().stream()
                .sorted()
                .forEach(id -> paths.add("/item/*," + id + "?legendary=on"));
        paths.add("/item?name=skrytka");
        Map<Long, CatalogItem> all = new LinkedHashMap<>();
        int fetched = 0;
        for (String path : paths) {
            Page first = parse(load(path));
            progress.accept(++fetched);
            int pages = Math.max(1, (first.total() + 223) / 224);
            if (pages > 100) throw new IllegalStateException("Unexpected page count");
            Set<Long> seen = new HashSet<>();
            for (int page = 1; page <= pages; page++) {
                Page data = page == 1 ? first : parse(load(path + "&p=" + page));
                if (page > 1) progress.accept(++fetched);
                int expected = Math.min(224, Math.max(0, first.total() - (page - 1) * 224));
                if (data.total() != first.total()
                        || data.rawCount() != expected
                        || data.ids().size() != data.rawCount())
                    throw new IllegalStateException("Incomplete catalog page");
                for (Long id : data.ids())
                    if (!seen.add(id)) throw new IllegalStateException("Repeated catalog page");
                for (var item : data.items()) all.put(item.externalId(), item);
            }
        }
        if (all.isEmpty()) throw new IllegalStateException("Empty catalog");
        return List.copyOf(all.values());
    }

    protected String load(String path) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        var response =
                Jsoup.connect(BASE + path)
                        .userAgent("MargoMarket catalog updater")
                        .timeout(30_000)
                        .maxBodySize(4_000_001)
                        .followRedirects(false)
                        .execute();
        if (response.statusCode() != 200)
            throw new IllegalStateException("Catalog source HTTP " + response.statusCode());
        if (response.bodyAsBytes().length > 4_000_000)
            throw new IllegalStateException("Catalog page too large");
        return response.body();
    }

    Page parse(String html) throws Exception {
        var document = Jsoup.parse(html, BASE);
        var count = Pattern.compile("(\\d+) przedmiotów spełnia kryteria").matcher(document.text());
        if (!count.find()) throw new IllegalStateException("Missing catalog page count");
        int total = Integer.parseInt(count.group(1));
        var nodes = document.select("a[href] span.margonem_item[data-itemtip]");
        List<CatalogItem> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (var node : nodes) {
            JsonNode tip = mapper.readTree(node.attr("data-itemtip"));
            long id = tip.path("id").asLong();
            if (id <= 0 || !tip.hasNonNull("name") || !tip.hasNonNull("stat"))
                throw new IllegalStateException("Invalid source item");
            ids.add(id);
            String stat = tip.path("stat").asText(), name = tip.path("name").asText();
            Map<String, String> entries = new HashMap<>();
            Set<String> flags = new HashSet<>();
            for (String part : stat.split(";")) {
                part = part.trim();
                int at = part.indexOf('=');
                if (at < 0) flags.add(part);
                else entries.put(part.substring(0, at), part.substring(at + 1));
            }
            if (!"legendary".equals(entries.get("rarity"))
                    || BLOCKED.stream().anyMatch(k -> flags.contains(k) || entries.containsKey(k)))
                continue;
            int level = 1;
            if (entries.containsKey("lvl")) level = Integer.parseInt(entries.get("lvl"));
            else {
                var range =
                        Pattern.compile("\\b(20|101|201)\\s*-\\s*(100|200|300)\\b")
                                .matcher(name + " " + stat);
                if (range.find()) level = Integer.parseInt(range.group(1));
            }
            int type = tip.path("cl").asInt();
            if (!name.toLowerCase(Locale.forLanguageTag("pl")).contains("skrytka")
                    && !(TYPES.containsKey(type) && level > 20 && flags.contains("binds")))
                continue;
            String description = entries.get("opis");
            if (description != null) {
                description = description.replace("[br]", "\n").trim();
                if (description.isEmpty()) description = null;
            }
            String href = node.closest("a").attr("href");
            if (!href.startsWith("/item/view/"))
                throw new IllegalStateException("Unexpected item link");
            result.add(
                    new CatalogItem(
                            id,
                            name,
                            "https://micc.garmory-cdn.cloud/obrazki/itemy/"
                                    + tip.path("icon").asText(),
                            level,
                            TYPES.getOrDefault(type, "Konsumpcyjne"),
                            "Legendarny",
                            description,
                            stat,
                            "margoworld",
                            BASE + href));
        }
        return new Page(total, nodes.size(), result, ids);
    }
}
