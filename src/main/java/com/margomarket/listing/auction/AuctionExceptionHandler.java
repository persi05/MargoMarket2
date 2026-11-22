package com.margomarket.listing.auction;

import com.margomarket.shared.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Too many bids in a short time. */
@RestControllerAdvice
@Order(GlobalExceptionHandler.MODULE_HANDLERS)
class AuctionExceptionHandler {
    @ExceptionHandler(AuctionRateLimitException.class)
    public ResponseEntity<java.util.Map<String,Object>> auctionRateLimit(AuctionRateLimitException ex) {
        return ResponseEntity.status(429).header("Retry-After",String.valueOf(ex.retryAfterSeconds))
                .body(java.util.Map.of("status",429,"message",ex.getMessage(),"retryAfterSeconds",ex.retryAfterSeconds));
    }
}
