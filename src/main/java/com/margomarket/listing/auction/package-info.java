/**
 * Auctions of item listings. Exposed as the named interface {@code listing::auction} because
 * conversations settle finished auctions (fulfillment) and enforce the auction rules on exchanges.
 */
@NamedInterface("auction")
package com.margomarket.listing.auction;

import org.springframework.modulith.NamedInterface;
