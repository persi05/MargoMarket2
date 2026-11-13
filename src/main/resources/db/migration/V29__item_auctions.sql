ALTER TABLE listings ADD COLUMN sale_mode VARCHAR(10) NOT NULL DEFAULT 'FIXED';
ALTER TABLE listings ADD CONSTRAINT listing_sale_mode CHECK (
    sale_mode IN ('FIXED', 'AUCTION') AND (sale_mode = 'FIXED' OR listing_kind = 'ITEM')
);
INSERT INTO listing_statuses(name) VALUES ('auction_ended') ON CONFLICT (name) DO NOTHING;

CREATE TABLE auctions (
    id BIGINT PRIMARY KEY REFERENCES listings(id),
    currency VARCHAR(4) NOT NULL CHECK (currency IN ('PLN', 'GOLD')),
    starting_amount BIGINT NOT NULL,
    original_ends_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED_WITH_WINNER','ENDED_NO_BIDS','CANCELLED')),
    highest_bid_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (starting_amount BETWEEN 1 AND CASE WHEN currency='PLN' THEN 100000000 ELSE 9000000000000000 END),
    CHECK (ends_at >= original_ends_at AND original_ends_at > created_at)
);
CREATE INDEX auction_due ON auctions(ends_at,id) WHERE status='ACTIVE';
ALTER TABLE auctions ADD CONSTRAINT auction_currency_key UNIQUE(id,currency);
CREATE TABLE auction_bids (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auctions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL,
    currency VARCHAR(4) NOT NULL CHECK (currency IN ('PLN','GOLD')),
    accepted_at TIMESTAMPTZ NOT NULL,
    idempotency_key UUID NOT NULL,
    UNIQUE(auction_id,user_id,idempotency_key),
    UNIQUE(auction_id,id),
    CHECK (amount BETWEEN 1 AND CASE WHEN currency='PLN' THEN 100000000 ELSE 9000000000000000 END)
);
ALTER TABLE auction_bids ADD CONSTRAINT auction_bid_currency FOREIGN KEY(auction_id,currency) REFERENCES auctions(id,currency);
ALTER TABLE auctions ADD CONSTRAINT auction_highest_bid FOREIGN KEY(id,highest_bid_id) REFERENCES auction_bids(auction_id,id);
CREATE INDEX auction_bid_history ON auction_bids(auction_id,id DESC);
CREATE INDEX auction_bid_user ON auction_bids(user_id,auction_id);
CREATE FUNCTION immutable_auction_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Auction history is append only'; END $$;
CREATE TRIGGER immutable_bids BEFORE UPDATE OR DELETE ON auction_bids FOR EACH ROW EXECUTE FUNCTION immutable_auction_history();
CREATE TABLE auction_bid_invalidations (
    bid_id BIGINT PRIMARY KEY REFERENCES auction_bids(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE auction_audit (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auctions(id),
    actor_id BIGINT REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_bid_invalidations BEFORE UPDATE OR DELETE ON auction_bid_invalidations FOR EACH ROW EXECUTE FUNCTION immutable_auction_history();
CREATE TRIGGER immutable_auction_audit BEFORE UPDATE OR DELETE ON auction_audit FOR EACH ROW EXECUTE FUNCTION immutable_auction_history();
CREATE TABLE auction_fulfillments (
    auction_id BIGINT PRIMARY KEY REFERENCES auctions(id),
    winner_id BIGINT NOT NULL REFERENCES users(id),
    conversation_id BIGINT UNIQUE REFERENCES conversations(id),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_CONTACT' CHECK(status IN ('PENDING_CONTACT','IN_PROGRESS','DECLARED_COMPLETED','ADMIN_CONFIRMED','DISPUTED','CANCELLED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE auction_reports (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auctions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES users(id),
    resolution VARCHAR(1000),
    UNIQUE(auction_id,user_id)
);
CREATE TABLE auction_user_restrictions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
    appeal_instructions VARCHAR(1000) NOT NULL CHECK(length(trim(appeal_instructions))>0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    revoked_at TIMESTAMPTZ,
    revoked_by BIGINT REFERENCES users(id),
    revoke_reason VARCHAR(1000),
    CHECK(expires_at>created_at)
);
CREATE INDEX auction_restriction_user ON auction_user_restrictions(user_id,expires_at) WHERE revoked_at IS NULL;
CREATE TABLE auction_bid_rate_limits (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    window_start TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE auction_bid_last_attempt (
    user_id BIGINT NOT NULL REFERENCES users(id),
    auction_id BIGINT NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(user_id,auction_id)
);
ALTER TABLE notifications ADD COLUMN auction_id BIGINT REFERENCES auctions(id);
ALTER TABLE notifications ADD COLUMN event_key VARCHAR(150) UNIQUE;
