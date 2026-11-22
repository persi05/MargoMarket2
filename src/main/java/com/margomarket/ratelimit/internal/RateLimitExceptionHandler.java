package com.margomarket.ratelimit.internal;

import com.margomarket.shared.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Request limits hit inside controllers (login attempts); the filter answers the rest itself. */
@RestControllerAdvice
@Order(GlobalExceptionHandler.MODULE_HANDLERS)
class RateLimitExceptionHandler {
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(exception.response());
    }
}
