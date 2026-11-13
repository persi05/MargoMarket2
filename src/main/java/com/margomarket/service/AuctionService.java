package com.margomarket.service;

import com.margomarket.dto.*;
import com.margomarket.exception.*;
import com.margomarket.mapper.ListingMapper;
import com.margomarket.model.*;
import com.margomarket.repository.*;
import com.margomarket.repository.AuctionRepository.State;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AuctionService {
    private final AuctionRepository auctions;
    private final JdbcTemplate jdbc;
    private final ListingService listings;
    private final ListingRepository listingRepository;
    private final CurrencyRepository currencies;
    private final ListingMapper mapper;
    private final AuctionBidRateLimiter limiter;
    private final AuctionRealtimePublisher realtime;
    private final NotificationService notifications;
    @Value("${app.features.item-auctions-enabled:false}") private boolean enabled;
    @Value("${app.features.pln-auctions-enabled:false}") private boolean plnEnabled;

    public Map<String,Object> config() {
        return Map.of("enabled", enabled, "plnEnabled", plnEnabled, "minimumHours", 1, "maximumDays", 7,
                "attemptsPerMinute", limiter.maximum(), "attemptSpacingSeconds", limiter.spacing());
    }

    @Transactional
    public AuctionResponse create(AuctionRequest r, User user) {
        requireEnabled(r.currency());
        if (!user.isEmailVerified()) throw new ForbiddenOperationException("Zweryfikuj e-mail");
        var now = auctions.now();
        AuctionRules.amount(r.currency(), r.startingAmount());
        AuctionRules.duration(now, r.endsAt());
        Listing l = listings.createListing(listingRequest(r), user);
        l.setSaleMode("AUCTION");
        l.setDescription(r.description());
        listingRepository.saveAndFlush(l);
        jdbc.update("INSERT INTO auctions(id,currency,starting_amount,original_ends_at,ends_at,created_at) VALUES (?,?,?,?,?,?)",
                l.getId(), r.currency(), r.startingAmount(), time(r.endsAt()), time(r.endsAt()), time(now));
        audit(l.getId(), user.getId(), "CREATED", null);
        return get(l.getId(), user);
    }

    @Transactional
    public AuctionResponse update(long id, AuctionRequest r, User user) {
        State a = auctions.lock(id);
        Listing l = listings.getListing(id);
        owner(l,user);
        requireEnabled(r.currency());
        if (!"ACTIVE".equals(a.status()) || !auctions.now().isBefore(a.endsAt()) || a.bidCount()>0)
            throw new IllegalArgumentException("Można edytować tylko aktywną aukcję bez ofert");
        AuctionRules.amount(r.currency(),r.startingAmount());
        AuctionRules.duration(auctions.now(),r.endsAt());
        listings.applyRequest(l,listingRequest(r));
        l.setDescription(r.description());
        listingRepository.flush();
        jdbc.update("UPDATE auctions SET currency=?,starting_amount=?,original_ends_at=?,ends_at=?,version=version+1 WHERE id=?",
                r.currency(),r.startingAmount(),time(r.endsAt()),time(r.endsAt()),id);
        audit(id,user.getId(),"EDITED",null);
        realtime.changed(id,a.version()+1);
        return get(id,user);
    }

    public AuctionResponse get(long id, User viewer) {
        State a = auctions.get(id);
        Listing l = listings.getListing(id);
        var fulfillments=fulfillments(List.of(id));
        return response(a,l,viewer,fulfillments.getOrDefault(id,Map.of()),auctions.now());
    }

    private AuctionResponse response(State a,Listing l,User viewer,Map<String,Object> f,Instant now) {
        long id=a.id();
        Long uid = viewer == null ? null : viewer.getId();
        boolean own = l.getUser().getId().equals(uid);
        boolean admin = viewer != null && viewer.isAdmin();
        Long winnerId = f.isEmpty() ? null : ((Number)f.get("winner_id")).longValue();
        boolean winner = Objects.equals(uid,winnerId) && uid != null;
        String fulfillment = (String)f.get("status");
        Long conversation = own||winner||admin ? (Long)f.get("conversation_id") : null;
        String winnerName = winnerId != null && (own||winner||admin)
                ? (String)f.get("winner_display_name") : null;
        Long next = a.highestAmount()==null ? a.startingAmount() : AuctionRules.next(a.currency(),a.highestAmount());
        return new AuctionResponse(id,mapper.toResponse(l,viewer!=null),a.currency(),viewer==null?null:a.startingAmount(),
                viewer==null?null:a.highestAmount(),viewer==null?null:next,a.originalEndsAt(),a.endsAt(),now,a.status(),a.version(),a.bidCount(),
                uid!=null && uid.equals(a.leaderId()),own,winner,own||winner||admin?fulfillment:null,conversation,
                "PENDING_CONTACT".equals(fulfillment) && now.isAfter(a.endsAt().plusSeconds(48*3600)),
                own||winner||admin?winnerId:null,winnerName);
    }

    private Map<Long,Map<String,Object>> fulfillments(List<Long> ids) {
        if(ids.isEmpty()) return Map.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        var rows=jdbc.queryForList("SELECT f.*, CASE WHEN u.display_name LIKE '%@%' THEN 'Gracz '||u.id ELSE u.display_name END winner_display_name FROM auction_fulfillments f JOIN users u ON u.id=f.winner_id WHERE f.auction_id IN ("+placeholders+")",ids.toArray());
        Map<Long,Map<String,Object>> result=new HashMap<>();
        rows.forEach(row->result.put(((Number)row.get("auction_id")).longValue(),row));
        return result;
    }

    public PageResponse<AuctionResponse> search(Long serverId, String currency, String search, int page, String scope, User viewer) {
        if (currency!=null && !currency.isBlank()) AuctionRules.limit(currency);
        if (!Set.of("ACTIVE","ENDED","SELLING","BIDDING","ADMIN").contains(scope)) throw new IllegalArgumentException("Nieznany widok");
        if (Set.of("SELLING","BIDDING","ADMIN").contains(scope) && viewer==null) throw new ForbiddenOperationException("Zaloguj się");
        if ("ADMIN".equals(scope) && !viewer.isAdmin()) throw new ForbiddenOperationException("Brak uprawnień");
        String condition = switch(scope) {
            case "SELLING" -> "l.user_id=" + viewer.getId();
            case "BIDDING" -> "EXISTS(SELECT 1 FROM auction_bids b WHERE b.auction_id=a.id AND b.user_id="+viewer.getId()+")";
            case "ENDED" -> "a.status<>'ACTIVE'";
            case "ADMIN" -> "true";
            default -> "a.status='ACTIVE' AND a.ends_at>clock_timestamp()";
        };
        String from = " FROM auctions a JOIN listings l ON l.id=a.id WHERE " + condition +
                " AND (?::bigint IS NULL OR l.server_id=?) AND (?='' OR a.currency=?) AND lower(l.item_name) LIKE ? ESCAPE '!'";
        String c=currency==null?"":currency;
        String s="%"+(search==null?"":search.trim().toLowerCase(Locale.ROOT)).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,serverId,serverId,c,c,s);
        int current=Math.clamp(page,1,1_000_000);
        var ids=jdbc.queryForList("SELECT a.id"+from+" ORDER BY a.ends_at,a.id LIMIT 20 OFFSET ?",Long.class,serverId,serverId,c,c,s,(current-1)*20);
        List<AuctionResponse> content=List.of();
        if(!ids.isEmpty()) {
            var states=auctions.getAll(ids).stream().collect(java.util.stream.Collectors.toMap(State::id,a->a));
            var offers=listingRepository.findDetailedByIds(ids).stream().collect(java.util.stream.Collectors.toMap(Listing::getId,l->l));
            var fulfillments=fulfillments(ids);
            var now=auctions.now();
            content=ids.stream().map(id->response(states.get(id),offers.get(id),viewer,fulfillments.getOrDefault(id,Map.of()),now)).toList();
        }
        return new PageResponse<>(content,current,20,total,(int)((total+19)/20),current==1,current*20L>=total);
    }

    public record Receipt(long id,long auctionId,long amount,String currency,Instant acceptedAt) {}
    private List<Receipt> receipt(long id, User user, UUID key) {
        return jdbc.query("SELECT * FROM auction_bids WHERE auction_id=? AND user_id=? AND idempotency_key=?",
                (r,n)->new Receipt(r.getLong("id"),id,r.getLong("amount"),r.getString("currency"),r.getObject("accepted_at",OffsetDateTime.class).toInstant()),id,user.getId(),key);
    }

    @Transactional
    public Receipt bid(long id, PlaceBidRequest r, User user) {
        var prior=receipt(id,user,r.idempotencyKey());
        if (!prior.isEmpty()) return replay(prior.getFirst(),r);
        long wait=limiter.attempt(user.getId(),id);
        if (wait>0) throw new AuctionRateLimitException(wait);
        State a=auctions.lock(id);
        prior=receipt(id,user,r.idempotencyKey());
        if (!prior.isEmpty()) return replay(prior.getFirst(),r);
        var now=auctions.now();
        requireEnabled(a.currency());
        if (!user.isEmailVerified() || user.getCreatedAt()==null || user.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().plusSeconds(24*3600).isAfter(now))
            throw new ForbiddenOperationException("Licytowanie wymaga zweryfikowanego e-maila i konta starszego niż 24 godziny");
        var restrictions=jdbc.queryForList("SELECT reason,appeal_instructions FROM auction_user_restrictions WHERE user_id=? AND revoked_at IS NULL AND expires_at>clock_timestamp() ORDER BY expires_at DESC LIMIT 1",user.getId());
        if (!restrictions.isEmpty()) throw new ForbiddenOperationException("Udział ograniczony: "+restrictions.getFirst().get("reason")+". Odwołanie: "+restrictions.getFirst().get("appeal_instructions"));
        Listing l=listings.getListing(id);
        if (l.getUser().getId().equals(user.getId())) throw new ForbiddenOperationException("Nie możesz licytować własnego przedmiotu");
        if (!"ACTIVE".equals(a.status()) || !now.isBefore(a.endsAt())) throw new IllegalArgumentException("Aukcja jest zakończona");
        if (!a.currency().equals(r.currency())) throw new IllegalArgumentException("Waluta oferty nie odpowiada aukcji");
        if (user.getId().equals(a.leaderId())) throw new IllegalArgumentException("Twoja oferta już prowadzi");
        AuctionRules.amount(r.currency(),r.amount());
        Long minimum=a.highestAmount()==null?a.startingAmount():AuctionRules.next(a.currency(),a.highestAmount());
        if (minimum==null || r.amount()<minimum) throw new IllegalArgumentException("Oferta jest niższa od minimalnej kolejnej kwoty");
        Long bidId=jdbc.queryForObject("INSERT INTO auction_bids(auction_id,user_id,amount,currency,accepted_at,idempotency_key) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,id,user.getId(),r.amount(),r.currency(),time(now),r.idempotencyKey());
        jdbc.update("UPDATE auctions SET highest_bid_id=?,ends_at=?,version=version+1 WHERE id=?",bidId,time(AuctionRules.extendedEnd(now,a.endsAt())),id);
        if (a.leaderId()!=null) notifications.auction(a.leaderId(),id,"AUCTION_OUTBID","Twoja oferta została przebita: "+l.getItemName(),"bid:"+bidId);
        realtime.changed(id,a.version()+1);
        return new Receipt(bidId,id,r.amount(),r.currency(),now);
    }

    public PageResponse<Map<String,Object>> bids(long id,int page,User viewer) {
        auctions.get(id);
        int current=Math.clamp(page,1,1_000_000);
        long total=jdbc.queryForObject("SELECT count(*) FROM auction_bids WHERE auction_id=?",Long.class,id);
        // Alias is the ordinal of a participant's first bid, scoped to this auction only.
        var rows=jdbc.query("""
            WITH participants AS (SELECT user_id,dense_rank() OVER(ORDER BY min(id)) alias FROM auction_bids WHERE auction_id=? GROUP BY user_id)
            SELECT b.*,p.alias,i.reason invalidation FROM auction_bids b JOIN participants p ON p.user_id=b.user_id
            LEFT JOIN auction_bid_invalidations i ON i.bid_id=b.id WHERE b.auction_id=? ORDER BY b.id DESC LIMIT 30 OFFSET ?
            """,(r,n)-> {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",r.getLong("id")); row.put("bidder","Licytujący "+r.getLong("alias"));
            row.put("own",viewer!=null && viewer.getId().equals(r.getLong("user_id")));
            row.put("amount",viewer==null?null:r.getLong("amount")); row.put("currency",r.getString("currency"));
            row.put("acceptedAt",r.getObject("accepted_at",OffsetDateTime.class).toInstant());
            row.put("invalidated",r.getString("invalidation")!=null);
            if (viewer!=null && viewer.isAdmin()) { row.put("userId",r.getLong("user_id")); row.put("reason",r.getString("invalidation")); }
            return row;
        },id,id,(current-1)*30);
        return new PageResponse<>(rows,current,30,total,(int)((total+29)/30),current==1,current*30L>=total);
    }

    @Transactional
    public void cancel(long id,User user,String reason) {
        State a=auctions.lock(id);
        Listing l=listings.getListing(id);
        if (!user.isAdmin()) { owner(l,user); if(a.bidCount()>0) throw new ForbiddenOperationException("Aukcję z ofertami może unieważnić administrator"); }
        reason=reason(reason);
        if (!"ACTIVE".equals(a.status())) throw new IllegalArgumentException("Aukcja nie jest aktywna");
        jdbc.update("UPDATE auctions SET status='CANCELLED',version=version+1 WHERE id=?",id);
        endListing(id);
        audit(id,user.getId(),"CANCELLED",reason);
        for(long uid:jdbc.queryForList("SELECT DISTINCT user_id FROM auction_bids WHERE auction_id=? UNION SELECT user_id FROM listings WHERE id=?",Long.class,id,id))
            notifications.auction(uid,id,"AUCTION_CANCELLED","Aukcja została anulowana: "+l.getItemName(),"cancel:"+id);
        realtime.changed(id,a.version()+1);
    }

    @Transactional
    public void report(long id,User user,String reason) {
        auctions.get(id);
        jdbc.update("INSERT INTO auction_reports(auction_id,user_id,reason) VALUES (?,?,?) ON CONFLICT(auction_id,user_id) DO NOTHING",id,user.getId(),reason(reason));
    }

    void endListing(long id) {
        jdbc.update("UPDATE listings SET status_id=(SELECT id FROM listing_statuses WHERE name='auction_ended'),version=version+1 WHERE id=?",id);
    }
    void audit(long id,Long actor,String action,String reason) {
        jdbc.update("INSERT INTO auction_audit(auction_id,actor_id,action,reason) VALUES (?,?,?,?)",id,actor,action,reason);
    }
    static String reason(String value) {
        if(value==null || value.isBlank() || value.length()>1000) throw new IllegalArgumentException("Podaj uzasadnienie (1–1000 znaków)");
        return value.trim();
    }
    static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private void requireEnabled(String currency) {
        if(!enabled || ("PLN".equals(currency) && !plnEnabled)) throw new ForbiddenOperationException("Licytacje w tej walucie nie zostały jeszcze uruchomione");
    }
    private void owner(Listing l,User user) {
        if(!l.getUser().getId().equals(user.getId())) throw new ForbiddenOperationException("Tylko sprzedający może edytować aukcję");
    }
    private Receipt replay(Receipt previous,PlaceBidRequest r) {
        if(previous.amount()!=r.amount() || !previous.currency().equals(r.currency())) throw new IllegalArgumentException("Klucz ponowienia został już użyty z inną ofertą");
        return previous;
    }
    private ListingRequest listingRequest(AuctionRequest r) {
        com.margomarket.model.Currency c=currencies.findByNameIgnoreCase("PLN".equals(r.currency())?"PLN":"w grze").orElseThrow(()->new NotFoundException("Brak waluty"));
        return new ListingRequest(r.itemId(),"Przedmiot",1L,1,r.enhancementLevel(),r.bound(),1L,1,c.getId(),r.serverId(),r.contact()==null?"":r.contact());
    }
}
