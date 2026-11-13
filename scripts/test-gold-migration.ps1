$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
# Apply all migrations to a disposable PostgreSQL, then check gold constraints.
$sql = ''
Get-ChildItem "$PSScriptRoot/../src/main/resources/db/migration/V*.sql" |
    Sort-Object { [int]([regex]::Match($_.Name, '^V(\d+)').Groups[1].Value) } |
    ForEach-Object { $sql += (Get-Content -Raw -Encoding utf8 $_.FullName) + "`n" }
$sql += @'
DO $$
DECLARE owner_id BIGINT; world_id BIGINT; currency_id_value BIGINT; status_id_value BIGINT; gold_id BIGINT;
BEGIN
 SELECT id INTO owner_id FROM users LIMIT 1;
 SELECT id INTO world_id FROM servers LIMIT 1;
 SELECT id INTO currency_id_value FROM currencies WHERE lower(name)='pln';
 SELECT id INTO status_id_value FROM listing_statuses WHERE name='active';
 INSERT INTO listings(user_id,item_name,currency_id,server_id,contact,status_id,listing_kind,gold_amount,price_minor,gold_rate_mode,rate_gold_units,rate_price_minor)
 VALUES(owner_id,'Złoto Margonem',currency_id_value,world_id,'',status_id_value,'GOLD',1500000000,3450,'PLN_PER_G',1000000000,2300) RETURNING id INTO gold_id;
 IF NOT EXISTS(SELECT 1 FROM active_listings_view WHERE id=gold_id) THEN RAISE EXCEPTION 'gold missing from view'; END IF;
 BEGIN UPDATE listings SET gold_amount=0 WHERE id=gold_id; RAISE EXCEPTION 'zero accepted'; EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN UPDATE listings SET gold_amount=9000000000000001 WHERE id=gold_id; RAISE EXCEPTION 'overflow accepted'; EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN UPDATE listings SET level=10 WHERE id=gold_id; RAISE EXCEPTION 'item payload accepted'; EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN UPDATE listings SET price_minor=0 WHERE id=gold_id; RAISE EXCEPTION 'zero price accepted'; EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
SELECT 'PASS: V1-V28, gold payload, bounds, views';
'@
$sql | docker run --rm -i --user postgres postgres:17-alpine sh -c 'initdb -D /tmp/gold-db -A trust >/dev/null && pg_ctl -D /tmp/gold-db -l /tmp/gold-pg.log start >/dev/null && psql -v ON_ERROR_STOP=1 -U postgres'
if ($LASTEXITCODE -ne 0) { throw 'Gold migration test failed' }
