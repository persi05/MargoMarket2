WITH mapping(bad_name, good_name) AS (
    VALUES
        ('ZwykĹ‚y', 'Zwykły')
),
pairs AS (
    SELECT bad.id AS bad_id, good.id AS good_id
    FROM mapping
    JOIN rarities bad ON bad.name = mapping.bad_name
    JOIN rarities good ON good.name = mapping.good_name
)
UPDATE listings
SET rarity_id = pairs.good_id
FROM pairs
WHERE listings.rarity_id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('ZwykĹ‚y', 'Zwykły')
),
pairs AS (
    SELECT bad.id AS bad_id, good.id AS good_id
    FROM mapping
    JOIN rarities bad ON bad.name = mapping.bad_name
    JOIN rarities good ON good.name = mapping.good_name
)
UPDATE items
SET rarity_id = pairs.good_id
FROM pairs
WHERE items.rarity_id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('ZwykĹ‚y', 'Zwykły')
),
pairs AS (
    SELECT bad.id AS bad_id
    FROM mapping
    JOIN rarities bad ON bad.name = mapping.bad_name
    JOIN rarities good ON good.name = mapping.good_name
)
DELETE FROM rarities
USING pairs
WHERE rarities.id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('ZwykĹ‚y', 'Zwykły')
)
UPDATE rarities
SET name = mapping.good_name
FROM mapping
WHERE rarities.name = mapping.bad_name
  AND NOT EXISTS (
      SELECT 1
      FROM rarities existing
      WHERE existing.name = mapping.good_name
  );

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('BroĹ„', 'Broń'),
        ('HeĹ‚m', 'Hełm'),
        ('RÄ™kawice', 'Rękawice'),
        ('PierĹ›cieĹ„', 'Pierścień')
),
pairs AS (
    SELECT bad.id AS bad_id, good.id AS good_id
    FROM mapping
    JOIN item_types bad ON bad.name = mapping.bad_name
    JOIN item_types good ON good.name = mapping.good_name
)
UPDATE listings
SET item_type_id = pairs.good_id
FROM pairs
WHERE listings.item_type_id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('BroĹ„', 'Broń'),
        ('HeĹ‚m', 'Hełm'),
        ('RÄ™kawice', 'Rękawice'),
        ('PierĹ›cieĹ„', 'Pierścień')
),
pairs AS (
    SELECT bad.id AS bad_id, good.id AS good_id
    FROM mapping
    JOIN item_types bad ON bad.name = mapping.bad_name
    JOIN item_types good ON good.name = mapping.good_name
)
UPDATE items
SET item_type_id = pairs.good_id
FROM pairs
WHERE items.item_type_id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('BroĹ„', 'Broń'),
        ('HeĹ‚m', 'Hełm'),
        ('RÄ™kawice', 'Rękawice'),
        ('PierĹ›cieĹ„', 'Pierścień')
),
pairs AS (
    SELECT bad.id AS bad_id
    FROM mapping
    JOIN item_types bad ON bad.name = mapping.bad_name
    JOIN item_types good ON good.name = mapping.good_name
)
DELETE FROM item_types
USING pairs
WHERE item_types.id = pairs.bad_id;

WITH mapping(bad_name, good_name) AS (
    VALUES
        ('BroĹ„', 'Broń'),
        ('HeĹ‚m', 'Hełm'),
        ('RÄ™kawice', 'Rękawice'),
        ('PierĹ›cieĹ„', 'Pierścień')
)
UPDATE item_types
SET name = mapping.good_name
FROM mapping
WHERE item_types.name = mapping.bad_name
  AND NOT EXISTS (
      SELECT 1
      FROM item_types existing
      WHERE existing.name = mapping.good_name
  );
