-- World groups and exchange rates used by price suggestions.
-- TRANSFERABLE worlds share one market; ISOLATED (no character transfers) and PRIVATE worlds are separate markets.
ALTER TABLE servers ADD COLUMN world_type VARCHAR(16) NOT NULL DEFAULT 'TRANSFERABLE'
    CHECK (world_type IN ('TRANSFERABLE', 'ISOLATED', 'PRIVATE'));
-- Gold units bought for 1 PLN, e.g. 18 000 000 for "1 zł = 18m".
ALTER TABLE servers ADD COLUMN gold_per_pln BIGINT CHECK (gold_per_pln > 0);
-- Price of 1 000 000 upgrade points in grosze.
ALTER TABLE servers ADD COLUMN upgrade_million_price_grosze BIGINT CHECK (upgrade_million_price_grosze > 0);
-- Price of one essence in grosze.
ALTER TABLE servers ADD COLUMN essence_price_grosze BIGINT CHECK (essence_price_grosze > 0);
ALTER TABLE servers ADD COLUMN rates_updated_at TIMESTAMPTZ;

INSERT INTO servers (name) VALUES ('Luvia'), ('Nerthus') ON CONFLICT (name) DO NOTHING;

WITH rates(name, world_type, gold_per_pln, upgrade_price, essence_price) AS (
    VALUES
        ('Luvia',    'ISOLATED',     18000000::BIGINT, 15000::BIGINT, 200::BIGINT),
        ('Aether',   'ISOLATED',     30000000, 7000,  50),
        ('Aldous',   'ISOLATED',     27000000, 7000,  50),
        ('Berufs',   'ISOLATED',     20000000, NULL,  NULL),
        ('Brutal',   'ISOLATED',     23000000, NULL,  NULL),
        ('Classic',  'TRANSFERABLE', 19000000, 11000, 100),
        ('Fobos',    'ISOLATED',     22000000, 10000, 100),
        ('Gefion',   'TRANSFERABLE', 24000000, 8000,  100),
        ('Gordion',  'ISOLATED',     17000000, 11000, 100),
        ('Hutena',   'TRANSFERABLE', 24000000, 9000,  100),
        ('Jaruna',   'TRANSFERABLE', 24000000, 9000,  100),
        ('Katahha',  'TRANSFERABLE', 21000000, 10000, 100),
        ('Lelwani',  'TRANSFERABLE', 19000000, 10000, 100),
        ('Majuna',   'TRANSFERABLE', 19000000, 10000, 100),
        ('Nomada',   'ISOLATED',     25000000, NULL,  NULL),
        ('Perkun',   'ISOLATED',     20000000, NULL,  NULL),
        ('Tarhuna',  'TRANSFERABLE', 22000000, 10000, 100),
        ('Telawel',  'TRANSFERABLE', 20000000, 10000, 100),
        ('Tempest',  'ISOLATED',     30000000, 7000,  50),
        ('Zemyna',   'TRANSFERABLE', 22000000, 10000, 100),
        ('Zorza',    'TRANSFERABLE', 24000000, 7000,  100),
        ('Nerthus',  'ISOLATED',     NULL,     NULL,  NULL),
        -- "1g = 20 zł" and "1g = 23 zł", rounded to whole gold units per PLN.
        ('Pandora',  'PRIVATE',      50000000, 5000,  50),
        ('Syberia',  'PRIVATE',      43478261, 5000,  50),
        ('Unia',     'PRIVATE',      43478261, 5000,  50)
)
UPDATE servers s
SET world_type = r.world_type,
    gold_per_pln = r.gold_per_pln,
    upgrade_million_price_grosze = r.upgrade_price,
    essence_price_grosze = r.essence_price,
    rates_updated_at = clock_timestamp()
FROM rates r
WHERE s.name = r.name;

CREATE INDEX idx_listings_item_prices ON listings(item_id, server_id) WHERE listing_kind = 'ITEM' AND item_id IS NOT NULL;
