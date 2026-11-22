package com.margomarket.conversation.internal;

import com.margomarket.listing.Listing;
import com.margomarket.listing.ListingService;
import com.margomarket.listing.auction.AuctionSettlement;
import com.margomarket.shared.Inputs;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;
import com.margomarket.user.UserDirectory;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuctionFulfillmentService {
    private final AuctionSettlement auctions;
    private final JdbcTemplate jdbc;
    private final ConversationRepository conversations;
    private final UserDirectory users;
    private final ListingService listings;
    private final ConversationAccess access;

    @Transactional
    public long start(long id,User user) {
        String auctionStatus=auctions.lock(id);
        var f=fulfillment(id);
        Listing listing=listings.getListing(id);
        long winner=((Number)f.get("winner_id")).longValue();
        participant(listing,winner,user);
        if(!"ENDED_WITH_WINNER".equals(auctionStatus) || Set.of("CANCELLED","DISPUTED").contains(f.get("status")))
            throw new IllegalArgumentException("Realizacja nie jest dostępna");
        if(f.get("conversation_id")!=null) return ((Number)f.get("conversation_id")).longValue();
        var c=conversations.findByListingAndBuyer(id,winner).orElse(null);
        if(c==null) {
            c=new Conversation(); c.setListing(listing); c.setItemName(listing.getItemName());
            c.setSeller(listing.getUser()); c.setBuyer(users.reference(winner)); c=conversations.saveAndFlush(c);
            access.saveSystem(c,"Rozmowa dotyczy realizacji wygranej licytacji. Serwis nie weryfikuje środków i nie realizuje płatności.");
        }
        jdbc.update("UPDATE auction_fulfillments SET conversation_id=?,status='IN_PROGRESS',updated_at=clock_timestamp() WHERE auction_id=?",c.getId(),id);
        auctions.audit(id,user.getId(),"CONTACT_STARTED",null);
        auctions.changed(id);
        return c.getId();
    }

    @Transactional
    public void action(long id,User user,String action,String reason) {
        auctions.lock(id);
        var f=fulfillment(id);
        var listing=listings.getListing(id);
        long winner=((Number)f.get("winner_id")).longValue();
        participant(listing,winner,user);
        String current=(String)f.get("status");
        if("DECLARE_COMPLETED".equals(action)) {
            if(!listing.getUser().getId().equals(user.getId()) || !"IN_PROGRESS".equals(current))
                throw new ForbiddenOperationException("Sprzedawca może zadeklarować zakończenie rozpoczętej wymiany");
            listings.markAsSold(id,user);
            return;
        }
        String next=switch(action) {
            case "DISPUTE" -> {
                if(Set.of("CANCELLED","DISPUTED","ADMIN_CONFIRMED").contains(current)) throw new IllegalArgumentException("Ten stan wymaga rozpatrzenia przez administrację");
                reason=Inputs.reason(reason); yield "DISPUTED";
            }
            case "CANCEL" -> {
                if(!user.isAdmin()) throw new ForbiddenOperationException("Anulowanie realizacji wymaga rozpatrzenia przez administratora");
                if("ADMIN_CONFIRMED".equals(current)) throw new IllegalArgumentException("Najpierw unieważnij potwierdzenie wymiany");
                reason=Inputs.reason(reason); yield "CANCELLED";
            }
            case "RESUME" -> {
                if(!user.isAdmin() || !Set.of("DISPUTED","CANCELLED").contains(current)) throw new ForbiddenOperationException("Tylko administrator może wznowić realizację");
                reason=Inputs.reason(reason); yield f.get("conversation_id")==null?"PENDING_CONTACT":"IN_PROGRESS";
            }
            default -> throw new IllegalArgumentException("Nieznana akcja");
        };
        jdbc.update("UPDATE auction_fulfillments SET status=?,updated_at=clock_timestamp() WHERE auction_id=?",next,id);
        auctions.audit(id,user.getId(),"FULFILLMENT_"+next,reason);
        auctions.changed(id);
    }
    private java.util.Map<String,Object> fulfillment(long id) {
        return jdbc.queryForList("SELECT * FROM auction_fulfillments WHERE auction_id=?",id).stream().findFirst().orElseThrow(()->new NotFoundException("Brak realizacji aukcji"));
    }
    private void participant(Listing l,long winner,User user) {
        if(!user.isAdmin() && !l.getUser().getId().equals(user.getId()) && winner!=user.getId()) throw new ForbiddenOperationException("Brak uprawnień do realizacji");
    }
}
