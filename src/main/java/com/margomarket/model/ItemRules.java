package com.margomarket.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Which catalog items can be upgraded or bound; mirrors {@code core/utils/item-types.ts} on the frontend. */
public final class ItemRules {
    private static final Set<String> NOT_UPGRADEABLE_TYPES = Set.of("Konsumpcyjne", "Talizmany", "Waluta", "Torby");
    private static final Set<String> NOT_BINDABLE_TYPES = Set.of("Konsumpcyjne", "Waluta");

    private ItemRules() {}

    public static boolean isSkrytka(Item item) {
        String normalizedName = Normalizer.normalize(item.getName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalizedName.contains("skrytk");
    }

    public static boolean isEnhancementLocked(Item item) {
        return isSkrytka(item) || NOT_UPGRADEABLE_TYPES.contains(item.getItemType().getName());
    }

    public static boolean canBind(Item item) {
        return !NOT_BINDABLE_TYPES.contains(item.getItemType().getName());
    }
}
