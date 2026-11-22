package com.margomarket.architecture;

import com.margomarket.catalog.internal.ItemCatalogImporter;
import com.margomarket.conversation.ExchangeConfirmed;
import com.margomarket.listing.auction.AuctionScheduler;
import com.margomarket.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Events between modules go through the outbox: they are stored in event_publication in the
 * publishing transaction, delivered after the commit and retried when a listener fails.
 * The exchange confirmation and the review prompt it triggers serve as the example.
 */
@SpringBootTest
@ActiveProfiles("auction-test")
@Testcontainers
class EventPublicationIT {
    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    static final Duration WAIT = Duration.ofSeconds(10);

    @MockBean ItemCatalogImporter importer;
    @MockBean AuctionScheduler scheduler;
    @MockBean NotificationService notifications;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transactions;
    @Autowired IncompleteEventPublications incomplete;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anEventIsStoredWithTheTransactionAndCompletedOnceTheListenerIsDone() {
        String item = uniqueItem();
        transactions.executeWithoutResult(status -> {
            events.publishEvent(confirmed(item));
            // Inside the transaction the listener has not run yet; the event only sits in the outbox.
            verify(notifications, never()).review(anyLong(), anyString(), anyString(), anyString());
        });

        await().atMost(WAIT).untilAsserted(() -> assertThat(completionDates(item)).singleElement().isNotNull());
        verify(notifications).review(eq(7L), eq("REVIEW_AVAILABLE"), contains(item), eq("review-available:" + item.hashCode()));
    }

    @Test
    void aRolledBackTransactionPublishesNothing() {
        String item = uniqueItem();
        transactions.executeWithoutResult(status -> {
            events.publishEvent(confirmed(item));
            status.setRollbackOnly();
        });

        assertThat(completionDates(item)).isEmpty();
        verify(notifications, never()).review(anyLong(), anyString(), contains(item), anyString());
    }

    @Test
    void aFailedListenerStaysInTheOutboxAndIsRetried() {
        String item = uniqueItem();
        doThrow(new IllegalStateException("powiadomienia chwilowo niedostępne")).doNothing()
                .when(notifications).review(anyLong(), anyString(), contains(item), anyString());

        transactions.executeWithoutResult(status -> events.publishEvent(confirmed(item)));

        // The listener failed, but the confirmation itself stays committed and the event waits for a retry.
        await().atMost(WAIT).untilAsserted(() ->
                verify(notifications).review(anyLong(), anyString(), contains(item), anyString()));
        assertThat(completionDates(item)).singleElement().isNull();

        incomplete.resubmitIncompletePublications(publication -> publication.getEvent() instanceof ExchangeConfirmed e
                && e.itemName().equals(item));

        await().atMost(WAIT).untilAsserted(() -> assertThat(completionDates(item)).singleElement().isNotNull());
        verify(notifications, times(2)).review(anyLong(), anyString(), contains(item), anyString());
    }

    /** Completion dates of the review prompt publications for one event (null while incomplete). */
    private java.util.List<java.sql.Timestamp> completionDates(String item) {
        return jdbc.queryForList("SELECT completion_date FROM event_publication WHERE listener_id LIKE '%ReviewPrompt%' "
                + "AND serialized_event LIKE ?", java.sql.Timestamp.class, "%" + item + "%");
    }

    private static ExchangeConfirmed confirmed(String item) {
        return new ExchangeConfirmed(item.hashCode(), 1, 7, 8, item);
    }

    private static String uniqueItem() {
        return "Miecz " + UUID.randomUUID();
    }
}
