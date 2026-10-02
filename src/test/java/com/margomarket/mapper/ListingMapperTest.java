package com.margomarket.mapper;

import com.margomarket.dto.ListingResponse;
import com.margomarket.model.Currency;
import com.margomarket.model.ItemType;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Rarity;
import com.margomarket.model.Server;
import com.margomarket.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListingMapperTest {

    private final ListingMapper mapper = new ListingMapper(new LookupMapper());

    @Test
    void hidesPriceContactAndSellerEmailFromGuests() {
        Listing listing = listing();

        ListingResponse publicResponse = mapper.toResponse(listing, false);
        ListingResponse privateResponse = mapper.toResponse(listing, true);

        assertThat(publicResponse.price()).isNull();
        assertThat(publicResponse.contact()).isNull();
        assertThat(publicResponse.sellerEmail()).isNull();
        assertThat(privateResponse.price()).isEqualTo(1500);
        assertThat(privateResponse.contact()).isEqualTo("Sprzedawca#123");
        assertThat(privateResponse.sellerEmail()).isEqualTo("seller@example.com");
    }

    private Listing listing() {
        User seller = new User();
        seller.setEmail("seller@example.com");

        ListingStatus status = new ListingStatus();
        status.setName("active");

        Listing listing = new Listing();
        listing.setUser(seller);
        listing.setItemType(new ItemType());
        listing.setRarity(new Rarity());
        listing.setCurrency(new Currency());
        listing.setServer(new Server());
        listing.setStatus(status);
        listing.setPrice(1500);
        listing.setContact("Sprzedawca#123");
        return listing;
    }
}
