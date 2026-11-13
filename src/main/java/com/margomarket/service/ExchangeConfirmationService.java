package com.margomarket.service;
import com.margomarket.model.*;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ExchangeConfirmationService {
 private final JdbcTemplate jdbc;
 private final ConversationRepository conversations;
 private final ConversationAccess access;
 private final ListingService listings;
 private final ConversationExchangeService exchanges;
 private final AuctionListingPolicy auctionPolicy;
 @Transactional
 public ConversationResponse confirm(Long id,User admin) {
  access.requireAdmin(admin);
  Long listingId=conversations.findDetailedById(id).filter(c -> c.getListing()!=null).map(c -> c.getListing().getId()).orElseThrow(() -> new NotFoundException("Brak ogłoszenia"));
  jdbc.queryForObject("SELECT id FROM listings WHERE id=? FOR UPDATE",Long.class,listingId);
  Conversation c=access.requireParticipant(id,admin,true);
  if(c.getIntermediary()==null || !admin.getId().equals(c.getIntermediary().getId()) || admin.getId().equals(c.getBuyer().getId()) || admin.getId().equals(c.getSeller().getId())) throw new ForbiddenOperationException("Tylko przypisany pośrednik może potwierdzić wymianę");
  var existing=jdbc.queryForList("SELECT id FROM exchange_confirmations WHERE conversation_id=?",Long.class,id);
  if(!existing.isEmpty()) return access.toResponse(c,admin);
  auctionPolicy.requireBuyer(c.getListing(),c.getBuyer().getId());
  if(c.getClosedAt()!=null || !c.getListing().getUser().getId().equals(c.getSeller().getId())) throw new IllegalArgumentException("Nie można potwierdzić tej wymiany");
  if(!jdbc.queryForList("SELECT id FROM exchange_confirmations WHERE listing_id=?",Long.class,listingId).isEmpty()) throw new IllegalArgumentException("Ogłoszenie ma już potwierdzoną wymianę");
  Long confirmation=jdbc.queryForObject("INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by) VALUES (?,?,?,?,?) RETURNING id",Long.class,id,listingId,c.getBuyer().getId(),c.getSeller().getId(),admin.getId());
  jdbc.update("INSERT INTO exchange_confirmation_audit(confirmation_id,actor_id,action) VALUES (?,?,'CONFIRMED')",confirmation,admin.getId());
  listings.markAsSold(listingId,admin);
  auctionPolicy.confirmed(c.getListing(),c.getBuyer().getId(),admin.getId());
  access.saveSystem(c,"Administrator potwierdził wykonanie wymiany. Potwierdzenie nie jest gwarancją bezpieczeństwa kolejnych transakcji.");
  return exchanges.close(id,admin,false);
 }
 @Transactional(readOnly=true)
 public com.margomarket.dto.PageResponse<java.util.Map<String,Object>> page(User admin,int page) {
  access.requireAdmin(admin);
  int current=Math.clamp(page,1,1000000);
  long total=jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations",Long.class);
  var rows=jdbc.queryForList("SELECT id,conversation_id,listing_id,buyer_id,seller_id,confirmed_by,confirmed_at,revoked_at,revoke_reason FROM exchange_confirmations ORDER BY id DESC LIMIT 20 OFFSET ?",(current-1)*20);
  return new com.margomarket.dto.PageResponse<>(rows,current,20,total,(int)((total+19)/20),current==1,current*20L>=total);
 }
 @Transactional
 public void revoke(Long id,User admin,String reason) {
  access.requireAdmin(admin);
  if(reason==null || reason.isBlank() || reason.length()>1000) throw new IllegalArgumentException("Podaj uzasadnienie (1–1000 znaków)");
  var listingIds=jdbc.queryForList("SELECT listing_id FROM exchange_confirmations WHERE id=?",Long.class,id);
  if(listingIds.isEmpty()) throw new NotFoundException("Potwierdzenie nie istnieje");
  Long listingId=listingIds.getFirst();
  jdbc.queryForList("SELECT id FROM listings WHERE id=? FOR UPDATE",Long.class,listingId);
  jdbc.queryForList("SELECT id FROM auctions WHERE id=? FOR UPDATE",Long.class,listingId);
  jdbc.queryForList("SELECT id FROM exchange_confirmations WHERE id=? FOR UPDATE",Long.class,id);
  if(jdbc.update("UPDATE exchange_confirmations SET revoked_at=clock_timestamp(),revoked_by=?,revoke_reason=? WHERE id=? AND revoked_at IS NULL",admin.getId(),reason.trim(),id)>0)
   {
    jdbc.update("INSERT INTO exchange_confirmation_audit(confirmation_id,actor_id,action,reason) VALUES (?,?,'REVOKED',?)",id,admin.getId(),reason.trim());
    auctionPolicy.confirmationRevoked(listingId,admin.getId(),reason.trim());
   }
 }
}
