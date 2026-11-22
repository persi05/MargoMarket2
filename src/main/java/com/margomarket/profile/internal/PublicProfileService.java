package com.margomarket.profile.internal;

import com.margomarket.listing.ListingQueries;
import com.margomarket.listing.ListingResponse;
import com.margomarket.shared.NotFoundException;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.PublicNames;
import com.margomarket.user.User;
import com.margomarket.user.UserDirectory;
import com.margomarket.listing.UserListingStats;
import com.margomarket.listing.UserStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PublicProfileService {
 private final UserDirectory users;
 private final ListingQueries listings;
 private final UserListingStats listingStats;
 private final JdbcTemplate jdbc;
 private User publicUser(Long id) { return users.findVerified(id).orElseThrow(() -> new NotFoundException("Profil niedostępny")); }
 public PublicProfileResponse get(Long id) {
  User user=publicUser(id);
  UserStats counts=listingStats.forUser(id);
  Long purchases=jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE buyer_id=? AND revoked_at IS NULL",Long.class,id);
  Long sales=jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE seller_id=? AND revoked_at IS NULL",Long.class,id);
  return new PublicProfileResponse(id,PublicNames.name(user),user.getCreatedAt(),counts.getActiveListings(),counts.getSoldListings(),purchases,sales,
   jdbc.queryForObject("SELECT count(*) FROM auctions a JOIN listings l ON l.id=a.id WHERE l.user_id=? AND a.status IN ('ENDED_WITH_WINNER','ENDED_NO_BIDS')",Long.class,id));
 }
 public PageResponse<ListingResponse> listings(Long id,int page,User viewer) {
  publicUser(id);
  return listings.publicActive(id,page,viewer!=null);
 }
}
