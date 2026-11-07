package com.margomarket.service;
import com.margomarket.dto.*;
import com.margomarket.model.User;
import com.margomarket.mapper.*;
import com.margomarket.repository.*;
import com.margomarket.service.PublicNames;
import com.margomarket.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PublicProfileService {
 private final UserRepository users;
 private final ListingRepository listings;
 private final ListingMapper mapper;
 private final PageMapper pages;
 private final JdbcTemplate jdbc;
 private User publicUser(Long id) { return users.findById(id).filter(User::isEmailVerified).orElseThrow(() -> new NotFoundException("Profil niedostępny")); }
 public PublicProfileResponse get(Long id) {
  User user=publicUser(id);
  Object[] counts=listings.countUserListingsByStatus(id).get(0);
  Long purchases=jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE buyer_id=? AND revoked_at IS NULL",Long.class,id);
  Long sales=jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE seller_id=? AND revoked_at IS NULL",Long.class,id);
  return new PublicProfileResponse(id,PublicNames.name(user),user.getCreatedAt(),((Number)counts[1]).longValue(),((Number)counts[2]).longValue(),purchases,sales);
 }
 public PageResponse<ListingResponse> listings(Long id,int page,User viewer) {
  publicUser(id);
  return pages.toResponse(listings.findPublicActive(id,PageRequest.of(Math.max(0,page-1),20)),l -> mapper.toResponse(l,viewer!=null));
 }
}
