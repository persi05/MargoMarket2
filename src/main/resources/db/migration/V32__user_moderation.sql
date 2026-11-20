CREATE TABLE user_moderation_actions (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('WARNING','RESTRICTION')),
 scope VARCHAR(24) CHECK(scope IN ('ACCOUNT','ITEM_LISTINGS','GOLD_LISTINGS','AUCTION_LISTINGS','BIDDING','MESSAGES','COMMENTS')),
 reason TEXT NOT NULL CHECK(length(trim(reason))>0), created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ, revoked_by BIGINT REFERENCES users(id) ON DELETE SET NULL, revoke_reason TEXT,
 legacy_auction_restriction_id BIGINT UNIQUE,
 CHECK((kind='WARNING' AND scope IS NULL AND expires_at IS NULL) OR (kind='RESTRICTION' AND scope IS NOT NULL)),
 CHECK(expires_at IS NULL OR expires_at>created_at),
 CHECK((revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL) OR (revoked_at IS NOT NULL AND length(trim(revoke_reason))>0))
);
CREATE INDEX user_moderation_history ON user_moderation_actions(user_id,id DESC);
CREATE INDEX user_moderation_active ON user_moderation_actions(user_id,scope,expires_at) WHERE kind='RESTRICTION' AND revoked_at IS NULL;
INSERT INTO user_moderation_actions(user_id,actor_id,kind,scope,reason,created_at,expires_at,revoked_at,revoked_by,revoke_reason,legacy_auction_restriction_id)
 SELECT user_id,actor_id,'RESTRICTION','BIDDING',reason||E'\nOdwołanie: '||appeal_instructions,created_at,expires_at,revoked_at,revoked_by,revoke_reason,id FROM auction_user_restrictions;
ALTER TABLE users ADD COLUMN account_block_id BIGINT REFERENCES user_moderation_actions(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN account_block_reason TEXT;
ALTER TABLE users ADD COLUMN account_block_until TIMESTAMPTZ;
ALTER TABLE notifications ALTER COLUMN message TYPE TEXT;
