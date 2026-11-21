-- Global price settings shared by all worlds. Dragon scales (SŁ) are bought for real money,
-- so one price is used to turn the unbinding cost into PLN for price suggestions.
CREATE TABLE pricing_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    -- Price of 100 dragon scales in grosze; NULL means bound and unbound prices are not converted.
    scales_hundred_price_grosze BIGINT CHECK (scales_hundred_price_grosze > 0),
    updated_at TIMESTAMPTZ
);

INSERT INTO pricing_settings (id) VALUES (1);
