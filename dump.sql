-- Drop existing tables
DROP TABLE IF EXISTS listing_favorites CASCADE;
DROP TABLE IF EXISTS listings CASCADE;
DROP TABLE IF EXISTS user_sessions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS servers CASCADE;
DROP TABLE IF EXISTS item_types CASCADE;
DROP TABLE IF EXISTS rarities CASCADE;
DROP TABLE IF EXISTS currencies CASCADE;
DROP TABLE IF EXISTS listing_statuses CASCADE;

-- =====================================================
-- LOOKUP TABLES (słowniki)
-- =====================================================

CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE servers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE item_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE rarities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE currencies (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE listing_statuses (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

-- =====================================================
-- TABLE: users
-- Relacja: Jeden-do-wielu z listings
-- Relacja: Wiele-do-jednego z roles
-- Relacja: Jeden-do-jednego z user_sessions
-- Relacja: Wiele-do-wielu z listings (przez listing_favorites)
-- =====================================================

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_user_role
        FOREIGN KEY (role_id) 
        REFERENCES roles(id)
        ON DELETE RESTRICT
);

-- =====================================================
-- TABLE: user_sessions
-- Każdy user ma maksymalnie jedną aktywną sesję
-- =====================================================

CREATE TABLE user_sessions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER UNIQUE NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_session_user
        FOREIGN KEY (user_id) 
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =====================================================
-- TABLE: listings
-- =====================================================

CREATE TABLE listings (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    
    item_name VARCHAR(255) NOT NULL,
    item_type_id INTEGER NOT NULL,
    level INTEGER NOT NULL,
    rarity_id INTEGER NOT NULL,
    
    price INTEGER NOT NULL,
    currency_id INTEGER NOT NULL,
    
    server_id INTEGER NOT NULL,
    
    contact VARCHAR(255) NOT NULL,
    status_id INTEGER NOT NULL,
    
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

-- =====================================================
-- TABLE: listing_favorites
-- Użytkownicy mogą dodawać ogłoszenia do ulubionych
-- =====================================================

CREATE TABLE listing_favorites (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    listing_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_favorite_user
        FOREIGN KEY (user_id) 
        REFERENCES users(id)
        ON DELETE CASCADE,
    
    CONSTRAINT fk_favorite_listing
        FOREIGN KEY (listing_id) 
        REFERENCES listings(id)
        ON DELETE CASCADE
);

-- =====================================================
-- INDEXES
-- =====================================================

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

-- =====================================================
-- WIDOK 1: active_listings_view (złączenie wielu tabel)
-- Pokazuje aktywne ogłoszenia z pełnymi informacjami
-- =====================================================

CREATE OR REPLACE VIEW active_listings_view AS
SELECT 
    l.id,
    l.item_name,
    it.name as item_type,
    l.level,
    r.name as rarity,
    l.price,
    c.name as currency,
    s.name as server,
    l.contact,
    l.created_at,
    u.id as user_id,
    u.email as user_email,
    ro.name as user_role
FROM listings l
INNER JOIN users u ON l.user_id = u.id
INNER JOIN servers s ON l.server_id = s.id
INNER JOIN item_types it ON l.item_type_id = it.id
INNER JOIN rarities r ON l.rarity_id = r.id
INNER JOIN currencies c ON l.currency_id = c.id
INNER JOIN listing_statuses ls ON l.status_id = ls.id
INNER JOIN roles ro ON u.role_id = ro.id
WHERE ls.name = 'active'
ORDER BY l.created_at DESC;

-- =====================================================
-- WIDOK 2: user_favorites_view
-- Pokazuje ulubione ogłoszenia użytkowników
-- =====================================================

CREATE OR REPLACE VIEW user_favorites_view AS
SELECT 
    f.id as favorite_id,
    f.user_id,
    u.email as user_email,
    l.id as listing_id,
    l.item_name,
    l.item_type_id,
    it.name as item_type,
    l.level,
    l.rarity_id,
    r.name as rarity,
    l.price,
    l.currency_id,
    c.name as currency,
    l.server_id,
    s.name as server,
    l.contact,
    l.status_id,
    ls.name as listing_status,
    f.created_at as favorited_at,
    l.created_at as listing_created_at,
    l.sold_at,
    l.user_id as owner_id,
    owner.email as owner_email
FROM listing_favorites f
INNER JOIN users u ON f.user_id = u.id
INNER JOIN listings l ON f.listing_id = l.id
INNER JOIN users owner ON l.user_id = owner.id
INNER JOIN servers s ON l.server_id = s.id
INNER JOIN item_types it ON l.item_type_id = it.id
INNER JOIN rarities r ON l.rarity_id = r.id
INNER JOIN currencies c ON l.currency_id = c.id
INNER JOIN listing_statuses ls ON l.status_id = ls.id
ORDER BY f.created_at DESC;

-- =====================================================
-- FUNKCJA: search_listings (z parametrami i filtrami)
-- =====================================================

CREATE OR REPLACE FUNCTION search_listings(
    p_search_term VARCHAR DEFAULT NULL,
    p_server_id INTEGER DEFAULT NULL,
    p_min_level INTEGER DEFAULT 0,
    p_max_level INTEGER DEFAULT 500,
    p_item_type_id INTEGER DEFAULT NULL,
    p_rarity_id INTEGER DEFAULT NULL,
    p_currency_id INTEGER DEFAULT NULL,
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    id INTEGER,
    user_id INTEGER,
    item_name VARCHAR,
    item_type_id INTEGER,
    item_type VARCHAR,
    level INTEGER,
    rarity_id INTEGER,
    rarity VARCHAR,
    price INTEGER,
    currency_id INTEGER,
    currency VARCHAR,
    server_id INTEGER,
    server VARCHAR,
    contact VARCHAR,
    status_id INTEGER,
    status VARCHAR,
    created_at TIMESTAMP,
    sold_at TIMESTAMP,
    user_email VARCHAR
) AS $$
DECLARE
    v_active_status_id INTEGER;
BEGIN
    SELECT ls.id INTO v_active_status_id 
    FROM listing_statuses ls 
    WHERE ls.name = 'active' 
    LIMIT 1;
    
    RETURN QUERY
    SELECT 
        l.id,
        l.user_id,
        l.item_name,
        l.item_type_id,
        it.name as item_type,
        l.level,
        l.rarity_id,
        r.name as rarity,
        l.price,
        l.currency_id,
        c.name as currency,
        l.server_id,
        s.name as server,
        l.contact,
        l.status_id,
        ls.name as status,

        l.created_at,
        l.sold_at,
        u.email as user_email
    FROM listings l
    INNER JOIN users u ON l.user_id = u.id
    INNER JOIN servers s ON l.server_id = s.id
    INNER JOIN item_types it ON l.item_type_id = it.id
    INNER JOIN rarities r ON l.rarity_id = r.id
    INNER JOIN currencies c ON l.currency_id = c.id
    INNER JOIN listing_statuses ls ON l.status_id = ls.id
    WHERE 
        l.status_id = v_active_status_id
        AND (p_server_id IS NULL OR l.server_id = p_server_id)
        AND (p_item_type_id IS NULL OR l.item_type_id = p_item_type_id)
        AND (p_rarity_id IS NULL OR l.rarity_id = p_rarity_id)
        AND (p_currency_id IS NULL OR l.currency_id = p_currency_id)
        AND l.level BETWEEN p_min_level AND p_max_level
        AND (p_search_term IS NULL OR l.item_name ILIKE '%' || p_search_term || '%')
    ORDER BY l.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql STABLE;

-- =====================================================
-- TRIGGER: auto_set_sold_at
-- Automatycznie ustawia sold_at gdy status zmienia się na 'sold'
-- =====================================================

CREATE OR REPLACE FUNCTION set_sold_timestamp()
RETURNS TRIGGER AS $$
DECLARE
    v_old_status_name VARCHAR;
    v_new_status_name VARCHAR;
BEGIN

    SELECT name INTO v_old_status_name FROM listing_statuses WHERE id = OLD.status_id;
    SELECT name INTO v_new_status_name FROM listing_statuses WHERE id = NEW.status_id;
    
    IF v_new_status_name = 'sold' AND v_old_status_name != 'sold' THEN
        NEW.sold_at = CURRENT_TIMESTAMP;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auto_set_sold_timestamp
    BEFORE UPDATE ON listings
    FOR EACH ROW
    WHEN (NEW.status_id IS DISTINCT FROM OLD.status_id)
    EXECUTE FUNCTION set_sold_timestamp();

-- =====================================================
-- TRANSAKCJA: mark_listing_as_sold
-- Oznacza ogłoszenie jako sprzedane z walidacją właściciela
-- Wykonuje się w transakcji READ COMMITTED (domyślny)
-- Atomowa operacja: zmiana statusu + usunięcie z ulubionych
-- =====================================================

CREATE OR REPLACE FUNCTION mark_listing_as_sold(
    p_listing_id INTEGER,
    p_user_id INTEGER
)
RETURNS BOOLEAN AS $$
DECLARE
    v_sold_status_id INTEGER;
    v_active_status_id INTEGER;
    v_owner_id INTEGER;
    v_current_status_id INTEGER;
BEGIN
    SELECT user_id, status_id 
    INTO v_owner_id, v_current_status_id
    FROM listings
    WHERE id = p_listing_id
    FOR UPDATE;
    
    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION 'Ogłoszenie o ID % nie istnieje', p_listing_id;
    END IF;
    
    IF v_owner_id != p_user_id THEN
        RAISE EXCEPTION 'Nie masz uprawnień do oznaczenia tego ogłoszenia jako sprzedane';
    END IF;
    
    SELECT id INTO v_active_status_id FROM listing_statuses WHERE name = 'active';
    SELECT id INTO v_sold_status_id FROM listing_statuses WHERE name = 'sold';
    
    IF v_current_status_id != v_active_status_id THEN
        RAISE EXCEPTION 'Ogłoszenie nie jest aktywne (może być już sprzedane)';
    END IF;
    
    UPDATE listings
    SET status_id = v_sold_status_id
    WHERE id = p_listing_id;
    
    DELETE FROM listing_favorites
    WHERE listing_id = p_listing_id;
    
    RETURN TRUE;
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Błąd oznaczania jako sprzedane: %', SQLERRM;
        RAISE;
END;
$$ LANGUAGE plpgsql;


-- =====================================================
-- MargoMarket Sample Data - 100 listings
-- =====================================================

-- Insert roles
INSERT INTO roles (name) VALUES ('user'), ('admin');

-- Insert currencies
INSERT INTO currencies (name) VALUES ('w grze'), ('pln');

-- Insert listing statuses
INSERT INTO listing_statuses (name) VALUES ('active'), ('sold');

-- Insert rarities
INSERT INTO rarities (name) VALUES
('Zwykły'), ('Unikatowy'), ('Heroiczny'), ('Legendarny');

-- Insert item types
INSERT INTO item_types (name) VALUES
('Broń'), ('Tarcza'), ('Zbroja'), ('Hełm'), ('Rękawice'), ('Buty'), 
('Pierścień'), ('Naszyjnik'), ('Inne');

-- Insert servers (23 serwery)
INSERT INTO servers (name) VALUES
('Aether'), ('Aldous'), ('Berufs'), ('Brutal'),
('Classic'), ('Fobos'), ('Gefion'), ('Gordion'),
('Hutena'), ('Jaruna'), ('Katahha'), ('Lelwani'),
('Majuna'), ('Nomada'), ('Perkun'), ('Tarhuna'),
('Telawel'), ('Tempest'), ('Zemyna'), ('Zorza'),
('Syberia'), ('Unia'), ('Pandora');

-- Insert users (25 użytkowników + 1 admin)
-- Hasło dla wszystkich: password123
INSERT INTO users (email, password, role_id) VALUES
('admin@margomarket.pl', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'admin')),
('wojtek.kowalski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('anna.nowak@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('piotr.wisniewski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('kasia.wojcik@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('marcin.kaminski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('ola.lewandowska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('tomek.zielinski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('magda.szymanska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('adam.wozniak@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('ela.dabrowa@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('bartek.kozlowski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('monika.jankowska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('krzysztof.mazur@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('agata.krawczyk@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('pawel.piotrowski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('justyna.grabowska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('lukasz.nowakowski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('natalia.pawlak@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('damian.michalski@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('karolina.wojciechowska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('sebastian.krol@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('weronika.sikorska@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('mateusz.baran@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('dominika.szewczyk@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user')),
('patryk.adamczyk@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', (SELECT id FROM roles WHERE name = 'user'));

-- =====================================================
-- Insert 100 listings
-- =====================================================

DO $$
DECLARE
    user_ids INTEGER[] := ARRAY(SELECT id FROM users WHERE role_id = (SELECT id FROM roles WHERE name = 'user'));
    server_ids INTEGER[] := ARRAY(SELECT id FROM servers);
    item_type_ids INTEGER[] := ARRAY(SELECT id FROM item_types);
    rarity_ids INTEGER[] := ARRAY(SELECT id FROM rarities);
    
    v_gold INTEGER := (SELECT id FROM currencies WHERE name = 'w grze');
    v_pln INTEGER := (SELECT id FROM currencies WHERE name = 'pln');
    v_active INTEGER := (SELECT id FROM listing_statuses WHERE name = 'active');
    v_sold INTEGER := (SELECT id FROM listing_statuses WHERE name = 'sold');
    
    item_names TEXT[] := ARRAY[
        'Miecz Świtu', 'Topór Burzy', 'Łuk Cienia', 'Kostur Ognia', 'Sztylet Nocy',
        'Młot Gromu', 'Włócznia Światła', 'Kusza Mroku', 'Rapier Wiatru', 'Katana Lodu',
        'Zbroja Smoka', 'Pancerz Tytana', 'Tunika Elfów', 'Kolczuga Rycerza', 'Szata Maga',
        'Hełm Walecznych', 'Korona Królów', 'Kaptur Łowcy', 'Tiara Kapłanki', 'Maska Assassyna',
        'Rękawice Siły', 'Naramienniki Obrońcy', 'Dłonie Złodzieja', 'Łapy Bestii', 'Pazury Smoka',
        'Buty Szybkości', 'Sandały Wędrowca', 'Trzewiki Górskie', 'Pantofle Maga', 'Kozaki Wojownika',
        'Pierścień Mocy', 'Obrączka Życia', 'Sygnet Władcy', 'Pierścień Mrozu', 'Obrączka Ognia',
        'Amulet Szczęścia', 'Naszyjnik Ochrony', 'Talizman Siły', 'Medalion Wiedzy', 'Wisiorek Duszy',
        'Tarcza Herosa', 'Pawęż Obrońcy', 'Bukler Gladiatora', 'Aegis Świętości', 'Tarcza Berserka',
        'Pas Mistrza', 'Pas Wojownika', 'Opaska Czempiona', 'Pas Gladiatora', 'Pas Rycerza'
    ];
    
    suffixes TEXT[] := ARRAY['Zniszczenia', 'Mocy', 'Ochrony', 'Walki', 'Magia', 'Siły', 'Zręczności', 'Inteligencji', 'Wytrzymałości', 'Fortuny'];
    
    contacts TEXT[] := ARRAY[
        'Discord: Warrior#1234', 'Discord: Mage#5678', 'Discord: Ranger#9012', 
        'Discord: Knight#3456', 'Discord: Rogue#7890', 'Discord: Paladin#2345',
        'Discord: Druid#6789', 'Discord: Hunter#0123', 'Discord: Priest#4567',
        'Discord: Warlock#8901'
    ];
    
    i INTEGER;
    random_user INTEGER;
    random_server INTEGER;
    random_item_type INTEGER;
    random_rarity INTEGER;
    random_currency INTEGER;
    random_status INTEGER;
    random_level INTEGER;
    random_price INTEGER;
    full_item_name TEXT;
BEGIN
    FOR i IN 1..100 LOOP
        random_user := user_ids[1 + floor(random() * array_length(user_ids, 1))];
        random_server := server_ids[1 + floor(random() * array_length(server_ids, 1))];
        random_item_type := item_type_ids[1 + floor(random() * array_length(item_type_ids, 1))];
        random_rarity := rarity_ids[1 + floor(random() * array_length(rarity_ids, 1))];
        
        IF random() < 0.8 THEN
            random_currency := v_gold;
            random_price := 1000 + floor(random() * 500000)::INTEGER;
        ELSE
            random_currency := v_pln;
            random_price := 10 + floor(random() * 5000)::INTEGER;
        END IF;
        
        IF random() < 0.85 THEN
            random_status := v_active;
        ELSE
            random_status := v_sold;
        END IF;
        
        random_level := 1 + floor(random() * 300)::INTEGER;
        
        full_item_name := 
            item_names[1 + floor(random() * array_length(item_names, 1))] || ' ' ||
            suffixes[1 + floor(random() * array_length(suffixes, 1))];

        INSERT INTO listings (
            user_id, 
            item_name, 
            item_type_id, 
            level, 
            rarity_id, 
            price, 
            currency_id, 
            server_id, 
            contact, 
            status_id
        ) VALUES (
            random_user,
            full_item_name,
            random_item_type,
            random_level,
            random_rarity,
            random_price,
            random_currency,
            random_server,
            contacts[1 + floor(random() * array_length(contacts, 1))],
            random_status
        );
    END LOOP;

END $$;

-- =====================================================
-- Kilka ulubionych
-- =====================================================

DO $$
DECLARE
    user_ids INTEGER[] := ARRAY(SELECT id FROM users WHERE role_id = (SELECT id FROM roles WHERE name = 'user') LIMIT 10);
    listing_ids INTEGER[] := ARRAY(SELECT id FROM listings WHERE status_id = (SELECT id FROM listing_statuses WHERE name = 'active') LIMIT 20);
    i INTEGER;
BEGIN
    FOR i IN 1..30 LOOP
        BEGIN
            INSERT INTO listing_favorites (user_id, listing_id)
            VALUES (
                user_ids[1 + floor(random() * array_length(user_ids, 1))],
                listing_ids[1 + floor(random() * array_length(listing_ids, 1))]
            );
        EXCEPTION WHEN unique_violation THEN
        END;
    END LOOP;

END $$;