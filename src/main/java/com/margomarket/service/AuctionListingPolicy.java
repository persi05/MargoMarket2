package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.model.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionListingPolicy {
    private final JdbcTemplate jdbc;
    private final AuctionRealtimePublisher realtime;
    public void requireFixed(Listing listing) {
        if ("AUCTION".equals(listing.getSaleMode())) throw new ForbiddenOperationException("Użyj operacji w widoku licytacji");
    }
    public void requireSaleAllowed(Listing listing) {
        if (!"AUCTION".equals(listing.getSaleMode())) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM auctions a JOIN auction_fulfillments f ON f.auction_id=a.id
              WHERE a.id=? AND a.status='ENDED_WITH_WINNER' AND f.status IN ('IN_PROGRESS','DECLARED_COMPLETED','ADMIN_CONFIRMED'))
            """,Boolean.class,listing.getId()))) throw new ForbiddenOperationException("Najpierw zakończ licytację i rozpocznij realizację z jej zwycięzcą");
    }
    public void requireBuyer(Listing listing,long buyerId) {
        if (!"AUCTION".equals(listing.getSaleMode())) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auction_fulfillments WHERE auction_id=? AND winner_id=? AND status NOT IN ('CANCELLED','DISPUTED'))",Boolean.class,listing.getId(),buyerId)))
            throw new ForbiddenOperationException("Realizacja aukcji jest dostępna wyłącznie dla jej zwycięzcy");
    }

    public void declaredSold(Listing listing, long actorId) {
        if (!"AUCTION".equals(listing.getSaleMode())) return;
        if (jdbc.update("UPDATE auction_fulfillments SET status='DECLARED_COMPLETED',updated_at=clock_timestamp() WHERE auction_id=? AND status='IN_PROGRESS'",listing.getId())>0)
            changed(listing.getId(),actorId,"FULFILLMENT_DECLARED_COMPLETED",null);
    }

    public void confirmed(Listing listing,long buyerId,long actorId) {
        if (!"AUCTION".equals(listing.getSaleMode())) return;
        jdbc.update("UPDATE auction_fulfillments SET status='ADMIN_CONFIRMED',updated_at=clock_timestamp() WHERE auction_id=? AND winner_id=?",listing.getId(),buyerId);
        changed(listing.getId(),actorId,"FULFILLMENT_ADMIN_CONFIRMED",null);
    }

    public void confirmationRevoked(long listingId,long actorId,String reason) {
        if (jdbc.update("UPDATE auction_fulfillments SET status='DISPUTED',updated_at=clock_timestamp() WHERE auction_id=?",listingId)>0)
            changed(listingId,actorId,"CONFIRMATION_REVOKED",reason);
    }

    private void changed(long listingId,long actorId,String action,String reason) {
        Long version=jdbc.queryForObject("UPDATE auctions SET version=version+1 WHERE id=? RETURNING version",Long.class,listingId);
        jdbc.update("INSERT INTO auction_audit(auction_id,actor_id,action,reason) VALUES (?,?,?,?)",listingId,actorId,action,reason);
        realtime.changed(listingId,version);
    }
}
