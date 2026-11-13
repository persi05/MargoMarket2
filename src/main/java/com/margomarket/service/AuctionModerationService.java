package com.margomarket.service;

import com.margomarket.dto.PageResponse;
import com.margomarket.exception.*;
import com.margomarket.model.User;
import com.margomarket.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuctionModerationService {
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final JdbcTemplate jdbc;
    private final AuctionRealtimePublisher realtime;

    @Transactional
    public void invalidate(long id,long bidId,User admin,String reason) {
        admin(admin); reason=AuctionService.reason(reason);
        var a=auctions.lock(id);
        var bids=jdbc.queryForList("SELECT id FROM auction_bids WHERE id=? AND auction_id=?",Long.class,bidId,id);
        if(bids.isEmpty()) throw new NotFoundException("Oferta nie istnieje");
        if(jdbc.update("INSERT INTO auction_bid_invalidations(bid_id,actor_id,reason) VALUES (?,?,?) ON CONFLICT DO NOTHING",bidId,admin.getId(),reason)==0) return;
        if("ACTIVE".equals(a.status())) {
            Long leader=jdbc.queryForList("SELECT b.id FROM auction_bids b LEFT JOIN auction_bid_invalidations i ON i.bid_id=b.id WHERE b.auction_id=? AND i.bid_id IS NULL ORDER BY b.amount DESC,b.id LIMIT 1",Long.class,id).stream().findFirst().orElse(null);
            jdbc.update("UPDATE auctions SET highest_bid_id=?,version=version+1 WHERE id=?",leader,id);
        } else {
            jdbc.update("UPDATE auction_fulfillments SET status='DISPUTED',updated_at=clock_timestamp() WHERE auction_id=?",id);
            jdbc.update("UPDATE auctions SET version=version+1 WHERE id=?",id);
        }
        service.audit(id,admin.getId(),"BID_INVALIDATED",bidId+": "+reason);
        realtime.changed(id,a.version()+1);
    }

    @Transactional
    public void restrict(long uid,User admin,String reason,String appeal,Instant end) {
        admin(admin);
        if(end==null || !end.isAfter(auctions.now()) || end.isAfter(auctions.now().plusSeconds(365*86400L))) throw new IllegalArgumentException("Podaj termin ograniczenia (maksymalnie rok)");
        jdbc.update("INSERT INTO auction_user_restrictions(user_id,actor_id,reason,appeal_instructions,expires_at) VALUES (?,?,?,?,?)",uid,admin.getId(),AuctionService.reason(reason),AuctionService.reason(appeal),AuctionService.time(end));
    }
    @Transactional
    public void revoke(long id,User admin,String reason) {
        admin(admin);
        if(jdbc.update("UPDATE auction_user_restrictions SET revoked_at=clock_timestamp(),revoked_by=?,revoke_reason=? WHERE id=? AND revoked_at IS NULL",admin.getId(),AuctionService.reason(reason),id)==0) throw new NotFoundException("Brak aktywnego ograniczenia");
    }
    @Transactional
    public void resolveReport(long id,User admin,String reason) {
        admin(admin);
        if(jdbc.update("UPDATE auction_reports SET resolved_at=clock_timestamp(),resolved_by=?,resolution=? WHERE id=? AND resolved_at IS NULL",admin.getId(),AuctionService.reason(reason),id)==0) throw new NotFoundException("Brak otwartego zgłoszenia");
    }
    @Transactional(readOnly=true)
    public PageResponse<Map<String,Object>> page(String kind,int page,User admin) {
        admin(admin);
        String table=switch(kind) { case "reports"->"auction_reports"; case "restrictions"->"auction_user_restrictions"; case "audit"->"auction_audit"; default->throw new IllegalArgumentException("Nieznany rejestr"); };
        int current=Math.clamp(page,1,1_000_000);
        long total=jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);
        var rows=jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id DESC LIMIT 20 OFFSET ?",(current-1)*20);
        return new PageResponse<>(rows,current,20,total,(int)((total+19)/20),current==1,current*20L>=total);
    }
    private void admin(User user) { if(!user.isAdmin()) throw new ForbiddenOperationException("Brak uprawnień"); }
}
