$ErrorActionPreference = 'Stop'
# Tests only a disposable database, never the application's PostgreSQL.
$sql = 'CREATE TABLE users(id BIGINT PRIMARY KEY); CREATE TABLE listings(id BIGINT PRIMARY KEY); CREATE TABLE conversations(id BIGINT PRIMARY KEY); CREATE TABLE listing_statuses(name TEXT UNIQUE);'
$sql += Get-Content -Raw "$PSScriptRoot/../src/main/resources/db/migration/V26__exchange_confirmations.sql"
$sql += @'
INSERT INTO users VALUES (1),(2),(3);
INSERT INTO listings VALUES (1);
INSERT INTO conversations VALUES (1),(2);
INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by) VALUES (1,1,1,2,3);
DO $$ BEGIN
 BEGIN
  INSERT INTO exchange_confirmations(conversation_id,listing_id,buyer_id,seller_id,confirmed_by) VALUES (2,1,1,2,3);
  RAISE EXCEPTION 'duplicate accepted';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
END $$;
DELETE FROM users WHERE id=1;
DO $$ BEGIN
 IF (SELECT count(*) FROM exchange_confirmations WHERE seller_id=2) <> 1 THEN RAISE EXCEPTION 'lost reputation'; END IF;
END $$;
SELECT 'PASS: migration, duplicate prevention, account deletion';
'@
$sql | docker run --rm -i --user postgres postgres:17-alpine sh -c 'initdb -D /tmp/profile-db -A trust >/dev/null && pg_ctl -D /tmp/profile-db -l /tmp/profile-pg.log start >/dev/null && psql -v ON_ERROR_STOP=1 -U postgres'
if ($LASTEXITCODE -ne 0) { throw 'Profile migration test failed' }
