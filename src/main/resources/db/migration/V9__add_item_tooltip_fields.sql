ALTER TABLE items
    ADD COLUMN description TEXT,
    ADD COLUMN stats TEXT,
    ADD COLUMN source_url TEXT,
    ADD COLUMN market_enabled BOOLEAN DEFAULT TRUE NOT NULL;

CREATE INDEX idx_items_market_enabled ON items(market_enabled);
