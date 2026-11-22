package com.margomarket.moderation;

public enum ModerationScope {
    ACCOUNT("Korzystanie z konta"),
    ITEM_LISTINGS("Wystawianie przedmiotów"),
    GOLD_LISTINGS("Wystawianie złota"),
    AUCTION_LISTINGS("Wystawianie licytacji"),
    BIDDING("Licytowanie"),
    MESSAGES("Wysyłanie wiadomości"),
    COMMENTS("Komentowanie przedmiotów"),
    REVIEWS("Wystawianie opinii");
    public final String label;

    ModerationScope(String label) {
        this.label = label;
    }
}
