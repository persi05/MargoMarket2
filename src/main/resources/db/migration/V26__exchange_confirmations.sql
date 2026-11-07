CREATE TABLE exchange_confirmations (
 id BIGSERIAL PRIMARY KEY,
 conversation_id BIGINT UNIQUE REFERENCES conversations(id) ON DELETE SET NULL,
 listing_id BIGINT UNIQUE REFERENCES listings(id) ON DELETE SET NULL,
 buyer_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 seller_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 confirmed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 confirmed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 revoked_at TIMESTAMPTZ,
 revoked_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 revoke_reason VARCHAR(1000),
 CHECK (buyer_id <> seller_id),
 CHECK (confirmed_by <> buyer_id AND confirmed_by <> seller_id)
);
CREATE INDEX idx_confirmation_buyer ON exchange_confirmations(buyer_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_confirmation_seller ON exchange_confirmations(seller_id) WHERE revoked_at IS NULL;
CREATE TABLE exchange_confirmation_audit (
 id BIGSERIAL PRIMARY KEY,
 confirmation_id BIGINT NOT NULL REFERENCES exchange_confirmations(id),
 actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 action VARCHAR(20) NOT NULL CHECK(action IN ('CONFIRMED','REVOKED')),
 reason VARCHAR(1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO listing_statuses(name) VALUES ('archived') ON CONFLICT(name) DO NOTHING;
