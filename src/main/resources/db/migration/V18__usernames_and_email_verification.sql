ALTER TABLE users ADD COLUMN username VARCHAR(255);
UPDATE users SET username = email;
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN verification_code_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN verification_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN verification_sent_at TIMESTAMP;
ALTER TABLE users ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0;
