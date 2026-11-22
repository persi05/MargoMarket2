-- Opinie o sprzedawcach. Opinię wystawia wyłącznie kupujący z wymiany potwierdzonej
-- przez pośrednika (exchange_confirmations), najwyżej jedną na potwierdzenie.
CREATE TABLE seller_reviews (
 id BIGSERIAL PRIMARY KEY,
 confirmation_id BIGINT NOT NULL UNIQUE REFERENCES exchange_confirmations(id),
 seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
 comment VARCHAR(1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 hidden_at TIMESTAMPTZ,
 hidden_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 hide_reason VARCHAR(1000),
 CHECK (buyer_id <> seller_id),
 CHECK ((hidden_at IS NULL AND hidden_by IS NULL AND hide_reason IS NULL) OR (hidden_at IS NOT NULL AND hide_reason IS NOT NULL))
);
CREATE INDEX idx_seller_reviews_seller ON seller_reviews(seller_id, created_at DESC) WHERE hidden_at IS NULL;

-- Strony opinii muszą zgadzać się z potwierdzoną wymianą, niezależnie od kodu aplikacji.
CREATE FUNCTION seller_review_matches_confirmation() RETURNS trigger AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM exchange_confirmations c WHERE c.id = NEW.confirmation_id
   AND c.buyer_id = NEW.buyer_id AND c.seller_id = NEW.seller_id) THEN
  RAISE EXCEPTION 'review parties do not match the exchange confirmation' USING ERRCODE = '23514';
 END IF;
 IF TG_OP = 'UPDATE' AND (NEW.rating <> OLD.rating OR NEW.comment IS DISTINCT FROM OLD.comment
   OR NEW.created_at <> OLD.created_at) THEN
  RAISE EXCEPTION 'review content is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER seller_review_matches_confirmation BEFORE INSERT OR UPDATE ON seller_reviews
 FOR EACH ROW EXECUTE FUNCTION seller_review_matches_confirmation();

ALTER TABLE user_moderation_actions DROP CONSTRAINT user_moderation_actions_scope_check;
ALTER TABLE user_moderation_actions ADD CONSTRAINT user_moderation_actions_scope_check
 CHECK (scope IN ('ACCOUNT','ITEM_LISTINGS','GOLD_LISTINGS','AUCTION_LISTINGS','BIDDING','MESSAGES','COMMENTS','REVIEWS'));
