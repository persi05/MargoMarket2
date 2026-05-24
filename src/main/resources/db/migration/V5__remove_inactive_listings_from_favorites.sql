CREATE OR REPLACE FUNCTION remove_inactive_listing_from_favorites()
RETURNS TRIGGER AS $$
DECLARE
    v_status_name VARCHAR;
BEGIN
    SELECT name INTO v_status_name
    FROM listing_statuses
    WHERE id = NEW.status_id;

    IF v_status_name != 'active' THEN
        DELETE FROM listing_favorites
        WHERE listing_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS remove_inactive_listing_from_favorites_trigger ON listings;

CREATE TRIGGER remove_inactive_listing_from_favorites_trigger
    AFTER UPDATE OF status_id ON listings
    FOR EACH ROW
    WHEN (NEW.status_id IS DISTINCT FROM OLD.status_id)
    EXECUTE FUNCTION remove_inactive_listing_from_favorites();

DELETE FROM listing_favorites f
USING listings l
INNER JOIN listing_statuses ls ON l.status_id = ls.id
WHERE f.listing_id = l.id
  AND ls.name != 'active';
