import { execFileSync } from 'node:child_process';

export interface E2eUser { username: string; password: string; displayName: string; email: string; role: 'user' | 'admin' }

/** Test accounts; registration would need a mailed code, so they are created directly in the database. */
const PASSWORD = 'E2e-Haslo-123';
/** BCrypt hash of PASSWORD. */
export const PASSWORD_HASH = '$2b$10$XBJvwVTBYzcHr970lRYXWOoRD6Hx50FrCWq5AQC32ZnivaDBinv9q';

export const E2E_USER: E2eUser = { username: 'e2e_sprzedawca', password: PASSWORD, displayName: 'Sprzedawca E2E', email: 'e2e@example.test', role: 'user' };
export const E2E_BUYER: E2eUser = { username: 'e2e_kupujacy', password: PASSWORD, displayName: 'Kupujacy E2E', email: 'e2e-kupujacy@example.test', role: 'user' };
export const E2E_ADMIN: E2eUser = { username: 'e2e_admin', password: PASSWORD, displayName: 'Posrednik E2E', email: 'e2e-admin@example.test', role: 'admin' };
const ACCOUNTS = [E2E_USER, E2E_BUYER, E2E_ADMIN];

/** Runs SQL inside the e2e database container started from e2e/docker-compose.e2e.yaml. */
export function sql(statement: string): string {
  return execFileSync('docker', ['exec', '-i', 'margomarket-e2e-postgres', 'psql', '-U', 'e2e', '-d', 'e2e', '-v', 'ON_ERROR_STOP=1', '-tA'],
    { input: statement, encoding: 'utf8' });
}

/** Inserts (or resets) a verified account that is old enough to use every feature. */
export function upsertUser(user: E2eUser): void {
  sql(`
    INSERT INTO users(email, username, display_name, password, role_id, email_verified, created_at)
    VALUES ('${user.email}', '${user.username}', '${user.displayName}', '${PASSWORD_HASH}',
            (SELECT id FROM roles WHERE name = '${user.role}'), true, localtimestamp - interval '2 days')
    ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, email_verified = true, role_id = EXCLUDED.role_id;
  `);
}

export default function globalSetup(): void {
  ACCOUNTS.forEach(upsertUser);
  // Every run starts without the data earlier runs left behind (the local stack may be reused).
  const ids = `(SELECT id FROM users WHERE username IN (${ACCOUNTS.map((u) => `'${u.username}'`).join(', ')}))`;
  sql(`
    DELETE FROM seller_reviews WHERE buyer_id IN ${ids} OR seller_id IN ${ids};
    DELETE FROM exchange_confirmation_audit WHERE confirmation_id IN
      (SELECT id FROM exchange_confirmations WHERE buyer_id IN ${ids} OR seller_id IN ${ids});
    DELETE FROM exchange_confirmations WHERE buyer_id IN ${ids} OR seller_id IN ${ids};
    DELETE FROM conversations WHERE buyer_id IN ${ids} OR seller_id IN ${ids};
    -- Auctions keep their bid history, so their listings stay; they do not affect other tests.
    DELETE FROM listings WHERE user_id IN ${ids} AND id NOT IN (SELECT id FROM auctions);
  `);
}
