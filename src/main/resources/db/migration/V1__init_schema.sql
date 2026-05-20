CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE item_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE rarities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE currencies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE listing_statuses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE RESTRICT
);

CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_session_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE listings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    item_type_id BIGINT NOT NULL,
    level INTEGER NOT NULL,
    rarity_id BIGINT NOT NULL,
    price INTEGER NOT NULL,
    currency_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    contact VARCHAR(255) NOT NULL,
    status_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sold_at TIMESTAMP,
    CONSTRAINT fk_listing_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_listing_server
        FOREIGN KEY (server_id)
        REFERENCES servers(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_listing_item_type
        FOREIGN KEY (item_type_id)
        REFERENCES item_types(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_listing_rarity
        FOREIGN KEY (rarity_id)
        REFERENCES rarities(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_listing_currency
        FOREIGN KEY (currency_id)
        REFERENCES currencies(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_listing_status
        FOREIGN KEY (status_id)
        REFERENCES listing_statuses(id)
        ON DELETE RESTRICT,
    CONSTRAINT price_positive CHECK (price > 0),
    CONSTRAINT level_valid CHECK (level > 0 AND level <= 300),
    CONSTRAINT item_name_length CHECK (LENGTH(item_name) >= 3)
);

CREATE TABLE listing_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_favorite_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_favorite_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_listing_favorites_user_listing UNIQUE (user_id, listing_id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_sessions_token ON user_sessions(session_token);
CREATE INDEX idx_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_listings_user_id ON listings(user_id);
CREATE INDEX idx_listings_status_id ON listings(status_id);
CREATE INDEX idx_listings_server_id ON listings(server_id);
CREATE INDEX idx_listings_level ON listings(level);
CREATE INDEX idx_listings_item_type_id ON listings(item_type_id);
CREATE INDEX idx_listings_rarity_id ON listings(rarity_id);
CREATE INDEX idx_listings_created_at ON listings(created_at DESC);
CREATE INDEX idx_listings_item_name ON listings USING gin(to_tsvector('simple', item_name));
CREATE INDEX idx_favorites_user_id ON listing_favorites(user_id);
CREATE INDEX idx_favorites_listing_id ON listing_favorites(listing_id);
