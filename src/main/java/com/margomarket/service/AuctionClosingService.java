package com.margomarket.service;

import com.margomarket.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionClosingService {
    private final JdbcTemplate jdbc;
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final NotificationService notifications;
    private final AuctionRealtimePublisher realtime;

    @Transactional
    public void closeDue() {
        // Lock listings first, skipping work claimed by other schedulers or bidders.
        var ids=jdbc.queryForList("""
            SELECT l.id FROM listings l JOIN auctions a ON a.id=l.id
            WHERE a.status='ACTIVE' AND a.ends_at<=clock_timestamp()
            ORDER BY a.ends_at,l.id LIMIT 100 FOR UPDATE OF l SKIP LOCKED
            """,Long.class);
        for(long id:ids) {
            var a=auctions.lock(id);
            if(!"ACTIVE".equals(a.status()) || auctions.now().isBefore(a.endsAt())) continue;
            String status=a.leaderId()==null?"ENDED_NO_BIDS":"ENDED_WITH_WINNER";
            jdbc.update("UPDATE auctions SET status=?,version=version+1 WHERE id=?",status,id);
            service.endListing(id);
            if(a.leaderId()!=null) jdbc.update("INSERT INTO auction_fulfillments(auction_id,winner_id) VALUES (?,?)",id,a.leaderId());
            service.audit(id,null,status,null);
            var l=jdbc.queryForMap("SELECT user_id,item_name FROM listings WHERE id=?",id);
            notifications.auction(((Number)l.get("user_id")).longValue(),id,"AUCTION_ENDED","Licytacja zakończona: "+l.get("item_name"),"end:"+id);
            if(a.leaderId()!=null) notifications.auction(a.leaderId(),id,"AUCTION_WON","Wygrywasz licytację: "+l.get("item_name")+". Uzgodnij wymianę ze sprzedającym.","win:"+id);
            realtime.changed(id,a.version()+1);
        }
    }
}
