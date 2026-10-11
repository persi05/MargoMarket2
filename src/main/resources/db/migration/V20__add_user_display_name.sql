ALTER TABLE users ADD COLUMN display_name VARCHAR(32);
UPDATE users SET display_name = LEFT(username, 32);
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
