package com.margomarket.profile.internal;

import com.margomarket.listing.ListingResponse;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@Tag(name = "Profile", description = "Publiczne profile sprzedających")
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class PublicProfileController {
 private final PublicProfileService service;
 @GetMapping("/{id}") public PublicProfileResponse get(@PathVariable Long id){return service.get(id);}
 @GetMapping("/{id}/listings") public PageResponse<ListingResponse> listings(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@AuthenticationPrincipal User user){return service.listings(id,page,user);}
}
