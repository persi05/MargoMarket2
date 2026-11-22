import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the real backend and database.
 * 1. docker compose -f e2e/docker-compose.e2e.yaml up -d --build --wait
 * 2. npm run e2e   (starts ng serve on :4300 with a proxy to the e2e backend on :18080)
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  timeout: 60_000,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4300',
    locale: 'pl-PL',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npx ng serve --port 4300 --proxy-config e2e/proxy.e2e.json',
    url: 'http://localhost:4300',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000
  }
});
