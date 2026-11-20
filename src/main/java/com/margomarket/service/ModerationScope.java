package com.margomarket.service;

public enum ModerationScope {
    ACCOUNT("Korzystanie z konta"),
    ITEM_LISTINGS("Wystawianie przedmiotów"),
    GOLD_LISTINGS("Wystawianie złota"),
    AUCTION_LISTINGS("Wystawianie licytacji"),
    BIDDING("Licytowanie"),
    MESSAGES("Wysyłanie wiadomości"),
    COMMENTS("Komentowanie przedmiotów");
    public final String label;

    ModerationScope(String label) {
        this.label = label;
    }
}
