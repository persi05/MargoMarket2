package com.margomarket.listing.auction;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the conversation module needs to settle a finished auction: the row lock, the audit trail
 * and the realtime notice. The auction tables and the rest of the auction code stay internal.
 */
@NamedInterface("auction")
@Service
@RequiredArgsConstructor
public class AuctionSettlement {
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final AuctionRealtimePublisher realtime;
    private final JdbcTemplate jdbc;

    /** Locks the listing and then the auction row, as every auction writer does, and returns the auction status. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String lock(long auctionId) {
        return auctions.lock(auctionId).status();
    }

    /** Adds an entry to the append-only auction audit log. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(long auctionId, Long actorId, String action, String reason) {
        service.audit(auctionId, actorId, action, reason);
    }

    /** Bumps the auction version and tells open auction pages to reload. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(long auctionId) {
        Long version = jdbc.queryForObject("UPDATE auctions SET version=version+1 WHERE id=? RETURNING version", Long.class, auctionId);
        realtime.changed(auctionId, version);
    }
}
