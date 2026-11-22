import { execFileSync } from 'node:child_process';

/** Test account; the password hash is BCrypt of E2E_PASSWORD (registration would need a mailed code). */
export const E2E_USER = { username: 'e2e_sprzedawca', password: 'E2e-Haslo-123', displayName: 'Sprzedawca E2E' };
const PASSWORD_HASH = '$2b$10$XBJvwVTBYzcHr970lRYXWOoRD6Hx50FrCWq5AQC32ZnivaDBinv9q';

/** Runs SQL inside the e2e database container started from e2e/docker-compose.e2e.yaml. */
export function sql(statement: string): string {
  return execFileSync('docker', ['exec', '-i', 'margomarket-e2e-postgres', 'psql', '-U', 'e2e', '-d', 'e2e', '-v', 'ON_ERROR_STOP=1', '-tA'],
    { input: statement, encoding: 'utf8' });
}

export default function globalSetup(): void {
  sql(`
    INSERT INTO users(email, username, display_name, password, role_id, email_verified, created_at)
    VALUES ('e2e@example.test', '${E2E_USER.username}', '${E2E_USER.displayName}', '${PASSWORD_HASH}',
            (SELECT id FROM roles WHERE name = 'user'), true, localtimestamp - interval '2 days')
    ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, email_verified = true;
    -- Every run starts with no listings of the test account.
    DELETE FROM listings WHERE user_id = (SELECT id FROM users WHERE username = '${E2E_USER.username}');
  `);
}
