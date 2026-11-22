package com.margomarket.listing.comment;

import com.margomarket.shared.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Comment cooldowns and mutes, with the posting status the form needs to count down. */
@RestControllerAdvice
@Order(GlobalExceptionHandler.MODULE_HANDLERS)
class CommentExceptionHandler {
    @ExceptionHandler(CommentMutedException.class)
    public ResponseEntity<java.util.Map<String, Object>> muted(CommentMutedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(java.util.Map.of("status", 429, "message", exception.getMessage(),
                        "retryAfterSeconds", exception.getRetryAfterSeconds(),
                        "muteRemainingSeconds", exception.getRetryAfterSeconds(),
                        "postingStatus", exception.getPostingStatus()));
    }
    @ExceptionHandler(CommentCooldownException.class)
    public ResponseEntity<java.util.Map<String, Object>> cooldown(CommentCooldownException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(java.util.Map.of("status", 429, "message", exception.getMessage(),
                        "retryAfterSeconds", exception.getRetryAfterSeconds(),
                        "postingStatus", exception.getPostingStatus()));
    }
}
