package com.margomarket.shared;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Small input rules shared by modules that record moderation and auction decisions. */
public final class Inputs {
    private Inputs() {}

    /** A justification typed by a user or an administrator: 1–1000 characters, trimmed. */
    public static String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 1000)
            throw new IllegalArgumentException("Podaj uzasadnienie (1–1000 znaków)");
        return value.trim();
    }

    /** Instant as a UTC timestamp for JDBC parameters ({@code timestamptz}). */
    public static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
