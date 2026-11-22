package com.margomarket.listing;

import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Ogłoszenia", description = "Ogłoszenia przedmiotów i ulubione")
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final FavoriteService favoriteService;
    private final ListingMapper listingMapper;
    private final PageMapper pageMapper;
    private final ListingViewService listingViews;

    @GetMapping
    public PageResponse<ListingResponse> search(@ModelAttribute ListingFilter filter,
                                                @AuthenticationPrincipal User user, @RequestParam(defaultValue="false") boolean publicOnly) {
        return pageMapper.toResponse(listingService.searchActiveListings(filter),
                listing -> listingMapper.toResponse(listing, !publicOnly && user != null));
    }

    @GetMapping("/private-details")
    public List<PrivateListingDetails> details(@RequestParam List<Long> ids,@AuthenticationPrincipal User user) {
        if(ids.size()>50) throw new IllegalArgumentException("Maksymalnie 50 ofert");
        return listingService.getPrivateDetails(ids);
    }

    @GetMapping("/{id}")
    public ListingResponse getOne(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return listingMapper.toResponse(listingService.getListing(id), user != null);
    }

    @GetMapping("/mine")
    public PageResponse<ListingResponse> mine(@RequestParam(defaultValue = "1") int page,
                                               @AuthenticationPrincipal User user) {
        return listingViews.page(listingService.getUserListings(user, page), true);
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
        listingService.updateListing(id, request, user);
        return listingMapper.toResponse(listingService.getListing(id));
    }

    @PostMapping("/{id}/sold")
    public ListingResponse markAsSold(@PathVariable Long id,
                                      @AuthenticationPrincipal User user) {
        listingService.markAsSold(id, user);
        // The locked entity has lazy relations and the mapper runs after the transaction, so read it again with details.
        return listingMapper.toResponse(listingService.getListing(id));
    }

    @GetMapping("/favorites")
    public PageResponse<ListingResponse> favorites(@RequestParam(defaultValue = "1") int page,
                                                    @AuthenticationPrincipal User user) {
        return listingViews.page(favoriteService.getUserFavoriteListings(user, page), true);
    }

    @GetMapping("/favorites/ids")
    public List<Long> favoriteIds(@RequestParam List<Long> listingIds, @AuthenticationPrincipal User user) {
        return favoriteService.getUserFavoriteIds(user, listingIds);
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
