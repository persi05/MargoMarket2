package com.margomarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionRealtimePublisher {
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    public record Changed(long id, long version) {}
    public void changed(long id, long version) { events.publishEvent(new Changed(id, version)); }
    @TransactionalEventListener
    public void publish(Changed event) {
        try { messaging.convertAndSend("/topic/auctions/" + event.id(), event); }
        catch (RuntimeException ex) { log.warn("Auction {} realtime delivery failed", event.id(), ex); }
    }
}
