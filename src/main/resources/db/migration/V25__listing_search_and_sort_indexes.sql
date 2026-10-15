CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_listings_item_name_trgm
    ON listings USING gin (lower(item_name) gin_trgm_ops);

CREATE INDEX idx_listings_status_created
    ON listings (status_id, created_at DESC, id DESC);

CREATE INDEX idx_listings_user_created
    ON listings (user_id, created_at DESC, id DESC);

DROP INDEX idx_listings_item_name;
DROP INDEX idx_listings_status_id;
DROP INDEX idx_listings_user_id;
