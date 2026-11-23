import { defineConfig, devices } from '@playwright/test';

/**
 * DevHIS (KPJ nHIS) Playwright config.
 * Environment / credentials come from env vars so the same tests run on any environment:
 *   DEVHIS_URL  (default https://devhis.sancyberhad.com)
 *   DEVHIS_USER / DEVHIS_PASS  (login used by tests/helpers/auth.ts)
 */
export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.DEVHIS_URL ?? 'https://devhis.sancyberhad.com',
    viewport: { width: 1920, height: 1080 },
    actionTimeout: 15_000,
    navigationTimeout: 45_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } }],
});
