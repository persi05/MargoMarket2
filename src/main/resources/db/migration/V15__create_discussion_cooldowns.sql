CREATE TABLE discussion_cooldowns (
    listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    next_allowed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (listing_id, author_id)
);

-- Existing comments were stored as UTC LocalDateTime by the backend.
INSERT INTO discussion_cooldowns (listing_id, author_id, next_allowed_at)
SELECT listing_id, author_id, (MAX(created_at) AT TIME ZONE 'UTC') + INTERVAL '10 minutes'
FROM listing_comments
GROUP BY listing_id, author_id;
