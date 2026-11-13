package com.margomarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionScheduler {
    private final AuctionClosingService closing;
    @Scheduled(fixedDelayString="${app.auctions.close-interval-ms:5000}")
    public void tick() {
        try { closing.closeDue(); }
        catch(RuntimeException ex) { log.error("Could not close due auctions",ex); }
    }
}
