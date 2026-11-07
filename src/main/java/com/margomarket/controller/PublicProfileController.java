package com.margomarket.controller;
import com.margomarket.dto.*;
import com.margomarket.model.User;
import com.margomarket.service.PublicProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class PublicProfileController {
 private final PublicProfileService service;
 @GetMapping("/{id}") public PublicProfileResponse get(@PathVariable Long id){return service.get(id);}
 @GetMapping("/{id}/listings") public PageResponse<ListingResponse> listings(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@AuthenticationPrincipal User user){return service.listings(id,page,user);}
}
