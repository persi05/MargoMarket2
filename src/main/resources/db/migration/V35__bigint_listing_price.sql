-- Ceny ogłoszeń przedmiotów w złocie przekraczały zakres INTEGER (2 147 483 647 = ok. 2,1g),
-- więc ceny typu 6,5g były odrzucane. Kolumna przechodzi na BIGINT; widoki i funkcja
-- zależne od niej są odtwarzane bez zmian (poza typem zwracanej ceny).
DROP VIEW active_listings_view;
DROP VIEW user_favorites_view;
DROP FUNCTION search_listings(VARCHAR,BIGINT,INTEGER,INTEGER,BIGINT,BIGINT,BIGINT,INTEGER,INTEGER);

ALTER TABLE listings ALTER COLUMN price TYPE BIGINT;

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

CREATE OR REPLACE FUNCTION search_listings(
    p_search_term VARCHAR DEFAULT NULL,
    p_server_id BIGINT DEFAULT NULL,
    p_min_level INTEGER DEFAULT 0,
    p_max_level INTEGER DEFAULT 500,
    p_item_type_id BIGINT DEFAULT NULL,
    p_rarity_id BIGINT DEFAULT NULL,
    p_currency_id BIGINT DEFAULT NULL,
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    id BIGINT,
    user_id BIGINT,
    item_name VARCHAR,
    item_type_id BIGINT,
    item_type VARCHAR,
    level INTEGER,
    rarity_id BIGINT,
    rarity VARCHAR,
    price BIGINT,
    currency_id BIGINT,
    currency VARCHAR,
    server_id BIGINT,
    server VARCHAR,
    contact VARCHAR,
    status_id BIGINT,
    status VARCHAR,
    created_at TIMESTAMP,
    sold_at TIMESTAMP,
    user_email VARCHAR
) AS $$
DECLARE
    v_active_status_id BIGINT;
BEGIN
    SELECT ls.id INTO v_active_status_id
    FROM listing_statuses ls
    WHERE ls.name = 'active'
    LIMIT 1;

    RETURN QUERY
    SELECT
        l.id,
        l.user_id,
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
        ls.name AS status,
        l.created_at,
        l.sold_at,
        u.email AS user_email
    FROM listings l
    INNER JOIN users u ON l.user_id = u.id
    INNER JOIN servers s ON l.server_id = s.id
    INNER JOIN item_types it ON l.item_type_id = it.id
    INNER JOIN rarities r ON l.rarity_id = r.id
    INNER JOIN currencies c ON l.currency_id = c.id
    INNER JOIN listing_statuses ls ON l.status_id = ls.id
    WHERE
        l.status_id = v_active_status_id
        AND (p_server_id IS NULL OR l.server_id = p_server_id)
        AND (p_item_type_id IS NULL OR l.item_type_id = p_item_type_id)
        AND (p_rarity_id IS NULL OR l.rarity_id = p_rarity_id)
        AND (p_currency_id IS NULL OR l.currency_id = p_currency_id)
        AND l.level BETWEEN p_min_level AND p_max_level
        AND (p_search_term IS NULL OR l.item_name ILIKE '%' || p_search_term || '%')
    ORDER BY l.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql STABLE;
