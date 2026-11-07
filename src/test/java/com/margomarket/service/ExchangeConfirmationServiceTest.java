package com.margomarket.service;
import com.margomarket.model.*;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.exception.ForbiddenOperationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ExchangeConfirmationServiceTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);ConversationRepository conversations=mock(ConversationRepository.class);ConversationAccess access=mock(ConversationAccess.class);ListingService listings=mock(ListingService.class);ConversationExchangeService exchanges=mock(ConversationExchangeService.class);
 ExchangeConfirmationService service=new ExchangeConfirmationService(jdbc,conversations,access,listings,exchanges);
 @Test void rejectsNonAdminBeforeAccessingRecords(){User u=new User();doThrow(new ForbiddenOperationException("Forbidden")).when(access).requireAdmin(u);assertThatThrownBy(()->service.confirm(1L,u)).isInstanceOf(ForbiddenOperationException.class);verifyNoInteractions(jdbc,conversations,listings,exchanges);}
 @Test void requiresReasonForRevocation(){assertThatThrownBy(()->service.revoke(1L,new User()," ")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(jdbc);}

 @Test void retryDoesNotCreateAnotherConfirmation(){
  User admin=new User();admin.setId(3L);User buyer=new User();buyer.setId(1L);User seller=new User();seller.setId(2L);
  Listing listing=new Listing();listing.setId(9L);listing.setUser(seller);
  Conversation c=new Conversation();c.setId(7L);c.setListing(listing);c.setBuyer(buyer);c.setSeller(seller);c.setIntermediary(admin);
  when(conversations.findDetailedById(7L)).thenReturn(java.util.Optional.of(c));when(access.requireParticipant(7L,admin,true)).thenReturn(c);
  when(jdbc.queryForList("SELECT id FROM exchange_confirmations WHERE conversation_id=?",Long.class,7L)).thenReturn(java.util.List.of(5L));
  service.confirm(7L,admin);verifyNoInteractions(listings,exchanges);verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void unrelatedAdminCannotConfirm(){
  User admin=new User();admin.setId(3L);User buyer=new User();buyer.setId(1L);User seller=new User();seller.setId(2L);User other=new User();other.setId(4L);
  Listing listing=new Listing();listing.setId(9L);Conversation c=new Conversation();c.setListing(listing);c.setBuyer(buyer);c.setSeller(seller);c.setIntermediary(other);
  when(conversations.findDetailedById(7L)).thenReturn(java.util.Optional.of(c));when(access.requireParticipant(7L,admin,true)).thenReturn(c);
  assertThatThrownBy(()->service.confirm(7L,admin)).isInstanceOf(ForbiddenOperationException.class);verifyNoInteractions(listings,exchanges);
 }
}
