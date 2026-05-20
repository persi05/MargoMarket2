package com.margomarket.controller;

import com.margomarket.dto.ListingFilter;
import com.margomarket.dto.ListingRequest;
import com.margomarket.dto.ListingResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.mapper.ListingMapper;
import com.margomarket.mapper.PageMapper;
import com.margomarket.model.User;
import com.margomarket.service.FavoriteService;
import com.margomarket.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final FavoriteService favoriteService;
    private final ListingMapper listingMapper;
    private final PageMapper pageMapper;

    @GetMapping
    public PageResponse<ListingResponse> search(@ModelAttribute ListingFilter filter) {
        return pageMapper.toResponse(listingService.searchActiveListings(filter), listingMapper::toResponse);
    }

    @GetMapping("/{id}")
    public ListingResponse getOne(@PathVariable Long id) {
        return listingMapper.toResponse(listingService.getListing(id));
    }

    @GetMapping("/mine")
    public List<ListingResponse> mine(@AuthenticationPrincipal User user) {
        return listingService.getUserListings(user).stream()
                .map(listingMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@Valid @RequestBody ListingRequest request,
                                  @AuthenticationPrincipal User user) {
        return listingMapper.toResponse(listingService.createListing(request, user));
    }

    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ListingRequest request,
                                  @AuthenticationPrincipal User user) {
        return listingMapper.toResponse(listingService.updateListing(id, request, user));
    }

    @PostMapping("/{id}/sold")
    public ListingResponse markAsSold(@PathVariable Long id,
                                      @AuthenticationPrincipal User user) {
        return listingMapper.toResponse(listingService.markAsSold(id, user));
    }

    @GetMapping("/favorites")
    public List<ListingResponse> favorites(@AuthenticationPrincipal User user) {
        return favoriteService.getUserFavoriteListings(user).stream()
                .map(listingMapper::toResponse)
                .toList();
    }

    @PostMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@PathVariable Long id, @AuthenticationPrincipal User user) {
        favoriteService.addFavorite(user, id);
    }

    @DeleteMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long id, @AuthenticationPrincipal User user) {
        favoriteService.removeFavorite(user, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        listingService.deleteListing(id, user);
    }
}
