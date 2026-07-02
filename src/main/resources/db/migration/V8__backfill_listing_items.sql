WITH matches AS (
    SELECT DISTINCT ON (listing.id)
        listing.id AS listing_id,
        item.id AS item_id
    FROM listings listing
    JOIN items item
        ON LOWER(item.name) = LOWER(listing.item_name)
       AND item.level = listing.level
       AND item.item_type_id = listing.item_type_id
       AND item.rarity_id = listing.rarity_id
    WHERE listing.item_id IS NULL
    ORDER BY listing.id, item.external_id
)
UPDATE listings
SET item_id = matches.item_id
FROM matches
WHERE listings.id = matches.listing_id;
