CREATE TABLE listing_comments (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_comment_body_not_blank CHECK (LENGTH(TRIM(body)) > 0)
);

CREATE INDEX idx_listing_comments_listing_id_desc ON listing_comments(listing_id, id DESC);
