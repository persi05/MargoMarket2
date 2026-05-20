package com.margomarket.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validation
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(LocalDateTime.now(), status, error, message, Map.of());
    }

    public static ApiError validation(Map<String, String> validation) {
        return new ApiError(LocalDateTime.now(), 400, "Bad Request", "Dane wejściowe są niepoprawne", validation);
    }
}
