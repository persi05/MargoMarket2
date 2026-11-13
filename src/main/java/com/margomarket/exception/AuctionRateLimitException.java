package com.margomarket.exception;

public class AuctionRateLimitException extends RuntimeException {
    public final long retryAfterSeconds;
    public AuctionRateLimitException(long seconds) {
        super("Zbyt wiele prób licytowania. Spróbuj ponownie za " + seconds + " s.");
        retryAfterSeconds = seconds;
    }
}
