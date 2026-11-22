package com.margomarket.shared.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Looks after the event_publication table (the outbox of events between modules): hands events
 * whose listener failed to it again, and removes completed events after a week.
 */
@Component
@RequiredArgsConstructor
class EventPublicationMaintenance {
    /** Listeners finish within seconds, so a publication still open after this has failed. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    static final Duration KEEP_COMPLETED = Duration.ofDays(7);

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void retryFailed() {
        incomplete.resubmitIncompletePublicationsOlderThan(RETRY_AFTER);
    }

    @Scheduled(cron = "0 30 3 * * *")
    void removeCompleted() {
        completed.deletePublicationsOlderThan(KEEP_COMPLETED);
    }
}
