ALTER TABLE listings ADD COLUMN listing_kind VARCHAR(10) NOT NULL DEFAULT 'ITEM';
ALTER TABLE listings ADD COLUMN gold_amount BIGINT;
ALTER TABLE listings ADD COLUMN price_minor BIGINT;
ALTER TABLE listings ADD COLUMN description VARCHAR(2000);
ALTER TABLE listings ALTER COLUMN item_type_id DROP NOT NULL;
ALTER TABLE listings ALTER COLUMN rarity_id DROP NOT NULL;
ALTER TABLE listings ALTER COLUMN level DROP NOT NULL;
ALTER TABLE listings ALTER COLUMN price DROP NOT NULL;
ALTER TABLE listings ADD CONSTRAINT listing_kind_valid CHECK(listing_kind IN ('ITEM','GOLD'));
ALTER TABLE listings ADD CONSTRAINT listing_payload_valid CHECK (
 (listing_kind='ITEM' AND item_type_id IS NOT NULL AND rarity_id IS NOT NULL AND level IS NOT NULL AND price IS NOT NULL AND gold_amount IS NULL AND price_minor IS NULL)
 OR (listing_kind='GOLD' AND item_id IS NULL AND item_type_id IS NULL AND rarity_id IS NULL AND level IS NULL AND price IS NULL AND NOT is_bound AND enhancement_level=0 AND gold_amount BETWEEN 1 AND 9000000000000000 AND price_minor BETWEEN 1 AND 100000000 AND gold_amount IS NOT NULL AND price_minor IS NOT NULL)
);
CREATE FUNCTION enforce_gold_listing_currency() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.listing_kind='GOLD' AND NOT EXISTS(SELECT 1 FROM currencies WHERE id=NEW.currency_id AND lower(name)='pln') THEN RAISE EXCEPTION 'Gold listings require PLN'; END IF;
 IF TG_OP='UPDATE' AND OLD.listing_kind<>NEW.listing_kind THEN RAISE EXCEPTION 'Listing kind is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gold_listing_currency BEFORE INSERT OR UPDATE ON listings FOR EACH ROW EXECUTE FUNCTION enforce_gold_listing_currency();
INSERT INTO listing_statuses(name) VALUES('withdrawn') ON CONFLICT(name) DO NOTHING;
CREATE INDEX idx_gold_listings_world ON listings(listing_kind,status_id,server_id,created_at DESC,id DESC);
CREATE INDEX idx_gold_listings_price ON listings(status_id,price_minor,id DESC) WHERE listing_kind='GOLD';
CREATE INDEX idx_gold_listings_amount ON listings(status_id,gold_amount,id DESC) WHERE listing_kind='GOLD';

CREATE OR REPLACE VIEW active_listings_view AS
SELECT
    l.id,
    l.item_name,
    it.name AS item_type,
    l.level,
    r.name AS rarity,
    l.price,
    c.name AS currency,
    s.name AS server,
    l.contact,
    l.created_at,
    u.id AS user_id,
    u.email AS user_email,
    ro.name AS user_role
FROM listings l
INNER JOIN users u ON l.user_id = u.id
INNER JOIN servers s ON l.server_id = s.id
LEFT JOIN item_types it ON l.item_type_id = it.id
LEFT JOIN rarities r ON l.rarity_id = r.id
INNER JOIN currencies c ON l.currency_id = c.id
INNER JOIN listing_statuses ls ON l.status_id = ls.id
INNER JOIN roles ro ON u.role_id = ro.id
WHERE ls.name = 'active'
ORDER BY l.created_at DESC;

CREATE OR REPLACE VIEW user_favorites_view AS
SELECT
    f.id AS favorite_id,
    f.user_id,
    u.email AS user_email,
    l.id AS listing_id,
    l.item_name,
    l.item_type_id,
    it.name AS item_type,
    l.level,
    l.rarity_id,
    r.name AS rarity,
    l.price,
    l.currency_id,
    c.name AS currency,
    l.server_id,
    s.name AS server,
    l.contact,
    l.status_id,
    ls.name AS listing_status,
    f.created_at AS favorited_at,
    l.created_at AS listing_created_at,
    l.sold_at,
    l.user_id AS owner_id,
    owner.email AS owner_email
FROM listing_favorites f
INNER JOIN users u ON f.user_id = u.id
INNER JOIN listings l ON f.listing_id = l.id
INNER JOIN users owner ON l.user_id = owner.id
INNER JOIN servers s ON l.server_id = s.id
LEFT JOIN item_types it ON l.item_type_id = it.id
LEFT JOIN rarities r ON l.rarity_id = r.id
INNER JOIN currencies c ON l.currency_id = c.id
INNER JOIN listing_statuses ls ON l.status_id = ls.id
ORDER BY f.created_at DESC;


ALTER TABLE listings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
