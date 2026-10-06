CREATE INDEX idx_users_unverified_expiration
    ON users (verification_expires_at)
    WHERE email_verified = FALSE;
