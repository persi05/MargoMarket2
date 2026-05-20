INSERT INTO roles (name) VALUES ('user'), ('admin');

INSERT INTO currencies (name) VALUES ('w grze'), ('pln');

INSERT INTO listing_statuses (name) VALUES ('active'), ('sold');

INSERT INTO rarities (name) VALUES
('Zwykły'),
('Unikatowy'),
('Heroiczny'),
('Legendarny');

INSERT INTO item_types (name) VALUES
('Broń'),
('Tarcza'),
('Zbroja'),
('Hełm'),
('Rękawice'),
('Buty'),
('Pierścień'),
('Naszyjnik'),
('Inne');

INSERT INTO servers (name) VALUES
('Aether'), ('Aldous'), ('Berufs'), ('Brutal'),
('Classic'), ('Fobos'), ('Gefion'), ('Gordion'),
('Hutena'), ('Jaruna'), ('Katahha'), ('Lelwani'),
('Majuna'), ('Nomada'), ('Perkun'), ('Tarhuna'),
('Telawel'), ('Tempest'), ('Zemyna'), ('Zorza'),
('Syberia'), ('Unia'), ('Pandora');

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

DO $$
DECLARE
    user_ids BIGINT[] := ARRAY(SELECT id FROM users WHERE role_id = (SELECT id FROM roles WHERE name = 'user'));
    server_ids BIGINT[] := ARRAY(SELECT id FROM servers);
    item_type_ids BIGINT[] := ARRAY(SELECT id FROM item_types);
    rarity_ids BIGINT[] := ARRAY(SELECT id FROM rarities);
    v_gold BIGINT := (SELECT id FROM currencies WHERE name = 'w grze');
    v_pln BIGINT := (SELECT id FROM currencies WHERE name = 'pln');
    v_active BIGINT := (SELECT id FROM listing_statuses WHERE name = 'active');
    v_sold BIGINT := (SELECT id FROM listing_statuses WHERE name = 'sold');
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
    suffixes TEXT[] := ARRAY['Zniszczenia', 'Mocy', 'Ochrony', 'Walki', 'Magii', 'Siły', 'Zręczności', 'Inteligencji', 'Wytrzymałości', 'Fortuny'];
    contacts TEXT[] := ARRAY[
        'Discord: Warrior#1234', 'Discord: Mage#5678', 'Discord: Ranger#9012',
        'Discord: Knight#3456', 'Discord: Rogue#7890', 'Discord: Paladin#2345',
        'Discord: Druid#6789', 'Discord: Hunter#0123', 'Discord: Priest#4567',
        'Discord: Warlock#8901'
    ];
    i INTEGER;
    random_user BIGINT;
    random_server BIGINT;
    random_item_type BIGINT;
    random_rarity BIGINT;
    random_currency BIGINT;
    random_status BIGINT;
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

DO $$
DECLARE
    user_ids BIGINT[] := ARRAY(SELECT id FROM users WHERE role_id = (SELECT id FROM roles WHERE name = 'user') LIMIT 10);
    listing_ids BIGINT[] := ARRAY(SELECT id FROM listings WHERE status_id = (SELECT id FROM listing_statuses WHERE name = 'active') LIMIT 20);
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
