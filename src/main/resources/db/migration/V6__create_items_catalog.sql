CREATE TABLE items (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    icon_url TEXT NOT NULL,
    level INTEGER NOT NULL,
    item_type_id BIGINT NOT NULL,
    rarity_id BIGINT NOT NULL,
    source VARCHAR(50) DEFAULT 'margolab' NOT NULL,
    CONSTRAINT fk_item_item_type
        FOREIGN KEY (item_type_id)
        REFERENCES item_types(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_item_rarity
        FOREIGN KEY (rarity_id)
        REFERENCES rarities(id)
        ON DELETE RESTRICT,
    CONSTRAINT item_level_valid CHECK (level > 0 AND level <= 300)
);

ALTER TABLE listings ADD COLUMN item_id BIGINT;

INSERT INTO rarities (name) VALUES
('Zwykły'),
('Unikatowy'),
('Heroiczny'),
('Legendarny'),
('Ulepszony'),
('Artefakt')
ON CONFLICT (name) DO NOTHING;

INSERT INTO item_types (name) VALUES
('Jednoręczne'),
('Dwuręczne'),
('Półtoraręczne'),
('Tarcza'),
('Zbroja'),
('Hełm'),
('Buty'),
('Rękawice'),
('Pierścień'),
('Naszyjnik'),
('Pomocnicze'),
('Różdżki'),
('Orby'),
('Dystansowe'),
('Strzały'),
('Błogosławieństwa'),
('Neutralne'),
('Konsumpcyjne'),
('Klucze'),
('Questowe'),
('Talizmany'),
('Torby'),
('Książki'),
('Waluta'),
('Ulepszenia'),
('Teleporty'),
('Złoto'),
('Stroje')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE listings
    ADD CONSTRAINT fk_listing_item
    FOREIGN KEY (item_id)
    REFERENCES items(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_items_name_lower ON items (LOWER(name));
CREATE INDEX idx_items_item_type_id ON items(item_type_id);
CREATE INDEX idx_items_rarity_id ON items(rarity_id);
CREATE INDEX idx_listings_item_id ON listings(item_id);
