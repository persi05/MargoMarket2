ALTER TABLE listings ADD COLUMN gold_rate_mode VARCHAR(20);
ALTER TABLE listings ADD COLUMN rate_gold_units BIGINT;
ALTER TABLE listings ADD COLUMN rate_price_minor BIGINT;
-- Preserve the exact package price of any existing offers without inventing a market rate.
UPDATE listings SET gold_rate_mode='PACKAGE',rate_gold_units=gold_amount,rate_price_minor=price_minor WHERE listing_kind='GOLD';
ALTER TABLE listings ADD CONSTRAINT gold_rate_valid CHECK(
 (listing_kind='ITEM' AND gold_rate_mode IS NULL AND rate_gold_units IS NULL AND rate_price_minor IS NULL)
 OR (listing_kind='GOLD' AND gold_rate_mode IS NOT NULL AND rate_gold_units IS NOT NULL AND rate_price_minor IS NOT NULL
 AND rate_gold_units BETWEEN 1 AND 9000000000000000 AND rate_price_minor BETWEEN 1 AND 100000000
 AND ((gold_rate_mode='M_PER_PLN' AND rate_price_minor=100) OR (gold_rate_mode='PLN_PER_G' AND rate_gold_units=1000000000) OR (gold_rate_mode='PACKAGE' AND rate_gold_units=gold_amount AND rate_price_minor=price_minor))
 AND price_minor=div(gold_amount::numeric*rate_price_minor*2+rate_gold_units,rate_gold_units::numeric*2))
);
