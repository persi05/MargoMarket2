package com.margomarket.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class CatalogSourceTest {
    private final CatalogSource source = new CatalogSource(new ObjectMapper());

    private String page(String stat, String name) throws Exception {
        String tip =
                new ObjectMapper()
                        .writeValueAsString(
                                java.util.Map.of(
                                        "id",
                                        42,
                                        "name",
                                        name,
                                        "icon",
                                        "test.gif",
                                        "cl",
                                        1,
                                        "stat",
                                        stat));
        return "<center>1 przedmiotów spełnia kryteria</center><a href='/item/view/42/test'><span"
                   + " class='margonem_item' data-itemtip='"
                + tip.replace("\"", "&quot;")
                + "'></span></a>";
    }

    @Test
    void readsEntitiesStatsAndDescription() throws Exception {
        var parsed =
                source.parse(
                        page("rarity=legendary;lvl=100;binds;opis=Pierwsza[br]Druga", "Miecz"));
        assertThat(parsed.rawCount()).isEqualTo(1);
        assertThat(parsed.items())
                .singleElement()
                .satisfies(
                        i -> {
                            assertThat(i.externalId()).isEqualTo(42);
                            assertThat(i.description()).isEqualTo("Pierwsza\nDruga");
                        });
    }

    @Test
    void excludesBoundAndUntradeableItems() throws Exception {
        for (String blocked :
                java.util.List.of("permbound", "force_binding=1", "noauction", "soulbound"))
            assertThat(
                            source.parse(page("rarity=legendary;lvl=100;binds;" + blocked, "Miecz"))
                                    .items())
                    .isEmpty();
    }

    @Test
    void equipmentRequiresBindingAndLevelOverTwenty() throws Exception {
        assertThat(source.parse(page("rarity=legendary;lvl=20;binds", "Miecz")).items()).isEmpty();
        assertThat(source.parse(page("rarity=legendary;lvl=100", "Miecz")).items()).isEmpty();
        assertThat(source.parse(page("rarity=heroic;lvl=100;binds", "Miecz")).items()).isEmpty();
    }

    @Test
    void allowsLegendaryChestWithoutBinding() throws Exception {
        assertThat(source.parse(page("rarity=legendary;opis=101-200", "Skrytka")).items())
                .singleElement()
                .extracting(CatalogItem::level)
                .isEqualTo(101);
    }

    @Test
    void rejectsChangedOrBrokenSourceMarkup() {
        assertThatThrownBy(() -> source.parse("<html>Maintenance</html>"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                source.parse(
                                        "<center>1 przedmiotów spełnia kryteria</center><a href='/item/view/42/test'><span"
                                            + " class='margonem_item'"
                                            + " data-itemtip='bad'></span></a>"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void refusesIncompleteOrRepeatedPagesBeforeReturningCatalog() throws Exception {
        String valid = page("rarity=legendary;lvl=100;binds", "Miecz");
        CatalogSource incomplete =
                new CatalogSource(new ObjectMapper()) {
                    @Override
                    protected String load(String path) {
                        return valid.replace("1 przedmiotów", "225 przedmiotów");
                    }
                };
        assertThatThrownBy(() -> incomplete.fetch(n -> {}))
                .isInstanceOf(IllegalStateException.class);
    }
}
