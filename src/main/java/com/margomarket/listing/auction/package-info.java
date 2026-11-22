/**
 * Auctions of item listings. Internal to the listing module except for the types marked with
 * {@code @NamedInterface("auction")}: {@link com.margomarket.listing.auction.AuctionSettlement} and
 * {@link com.margomarket.listing.auction.AuctionListingPolicy}, which conversations use to settle
 * finished auctions and to apply the auction rules to exchanges.
 */
package com.margomarket.listing.auction;
