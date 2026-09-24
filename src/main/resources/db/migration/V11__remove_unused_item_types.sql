INSERT INTO item_types (name) VALUES
    ('Jednoręczne'), ('Dwuręczne'), ('Półtoraręczne'), ('Tarcza'), ('Dystansowe'),
    ('Pomocnicze'), ('Różdżki'), ('Orby'), ('Strzały'), ('Zbroja'), ('Hełm'),
    ('Buty'), ('Rękawice'), ('Pierścień'), ('Naszyjnik'), ('Talizmany'),
    ('Konsumpcyjne'), ('Waluta'), ('Torby')
ON CONFLICT (name) DO NOTHING;

DELETE FROM listings AS listing
USING item_types AS type
WHERE listing.item_type_id = type.id
  AND type.name IN ('Broń', 'Inne');

DELETE FROM item_types AS type
WHERE type.name NOT IN (
    'Jednoręczne', 'Dwuręczne', 'Półtoraręczne', 'Tarcza', 'Dystansowe',
    'Pomocnicze', 'Różdżki', 'Orby', 'Strzały', 'Zbroja', 'Hełm',
    'Buty', 'Rękawice', 'Pierścień', 'Naszyjnik', 'Talizmany',
    'Konsumpcyjne', 'Waluta', 'Torby'
)
AND NOT EXISTS (SELECT 1 FROM items WHERE item_type_id = type.id)
AND NOT EXISTS (SELECT 1 FROM listings WHERE item_type_id = type.id);
