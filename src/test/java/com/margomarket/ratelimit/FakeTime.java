package com.margomarket.ratelimit;

import io.github.bucket4j.TimeMeter;

import java.time.Duration;

class FakeTime implements TimeMeter {
    private long nanos = 1_000_000_000L;

    void advance(Duration duration) {
        nanos += duration.toNanos();
    }

    @Override
    public long currentTimeNanos() {
        return nanos;
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }
}
