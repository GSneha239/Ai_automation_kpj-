/**
 * Runs the existing tests the way the Java suite runs: the Playwright-installed Chromium in a visible, maximized window
 * (no fixed viewport), a screenshot at the end of every test, and the report written in the Java suite's standard HTML
 * format into devhis-tests-share/test-output/reports (see reporters/devhisReport.ts).
 *
 *   npx playwright test -c playwright.devhis.config.ts tests/Billing_page/doctorShare.spec.ts
 *
 * The base playwright.config.ts is not changed. Environment / login still come from DEVHIS_URL, DEVHIS_USER, DEVHIS_PASS
 * (Doctor Share also reads DEVHIS_LOGIN_URL). The browser is $PW_CHROMIUM_EXE, else the Playwright Chromium the Java
 * suite uses; if that file is not there, Playwright's own default browser is used.
 */
import { defineConfig } from '@playwright/test';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import base from './playwright.config';

const CANDIDATE = process.env.PW_CHROMIUM_EXE ?? 'C:/Users/ABCOM/AppData/Local/ms-playwright/chromium-1091/chrome-win/chrome.exe';
const executablePath = fs.existsSync(CANDIDATE) ? CANDIDATE : undefined;

const visibleMaximized = {
  headless: false,
  viewport: null,
  deviceScaleFactor: undefined,   // the "Desktop Chrome" preset sets it, and it cannot be combined with a null viewport
  screenshot: 'on' as const,
  trace: 'off' as const,          // no trace files, so no login data is stored
  launchOptions: { ...(executablePath ? { executablePath } : {}), args: ['--start-maximized'] },
};

export default defineConfig({
  ...base,
  // Playwright empties its output folder at the start of a run, so keep it away from test-results/.
  outputDir: path.join(os.tmpdir(), 'devhis-pw-output'),
  reporter: [['list'], ['./reporters/devhisReport.ts']],
  use: { ...base.use, ...visibleMaximized },
  projects: (base.projects ?? []).map((p) => ({ ...p, use: { ...p.use, ...visibleMaximized } })),
});
