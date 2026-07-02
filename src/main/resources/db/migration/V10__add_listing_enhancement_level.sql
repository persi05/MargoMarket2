ALTER TABLE listings
    ADD COLUMN enhancement_level INTEGER DEFAULT 0 NOT NULL,
    ADD CONSTRAINT listing_enhancement_level_valid CHECK (enhancement_level >= 0 AND enhancement_level <= 5);
