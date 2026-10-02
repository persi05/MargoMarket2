CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT REFERENCES listings(id) ON DELETE SET NULL,
    item_name VARCHAR(255) NOT NULL,
    buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    intermediary_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    intermediary_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    buyer_last_read_id BIGINT NOT NULL DEFAULT 0,
    seller_last_read_id BIGINT NOT NULL DEFAULT 0,
    intermediary_last_read_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversation_different_users CHECK (buyer_id <> seller_id),
    CONSTRAINT conversation_intermediary_status CHECK (intermediary_status IN ('NONE', 'REQUESTED', 'ASSIGNED')),
    CONSTRAINT conversation_unique_buyer_listing UNIQUE (listing_id, buyer_id)
);

CREATE INDEX idx_conversations_buyer_updated ON conversations(buyer_id, updated_at DESC);
CREATE INDEX idx_conversations_seller_updated ON conversations(seller_id, updated_at DESC);
CREATE INDEX idx_conversations_intermediary_updated ON conversations(intermediary_id, updated_at DESC);
CREATE INDEX idx_conversations_requested ON conversations(updated_at DESC) WHERE intermediary_status = 'REQUESTED';

CREATE TABLE conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    kind VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversation_message_kind CHECK (kind IN ('TEXT', 'SYSTEM')),
    CONSTRAINT conversation_message_body CHECK (LENGTH(TRIM(body)) > 0)
);

CREATE INDEX idx_conversation_messages_latest ON conversation_messages(conversation_id, id DESC);
