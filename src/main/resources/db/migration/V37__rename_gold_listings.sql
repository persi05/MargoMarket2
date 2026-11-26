-- Gold listings are named just "Złoto"; earlier ones were saved as "Złoto Margonem".
UPDATE listings SET item_name = 'Złoto' WHERE listing_kind = 'GOLD' AND item_name = 'Złoto Margonem';
