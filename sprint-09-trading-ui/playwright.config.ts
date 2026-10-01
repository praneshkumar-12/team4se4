import path from 'node:path';

import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

dotenv.config({ path: path.resolve(__dirname, '.env') });

const baseURL = process.env['E2E_BASE_URL'];
if (!baseURL) {
  throw new Error(
    'E2E_BASE_URL is not set. Copy .env.example to .env and fill in real values before running npm run e2e (see e2e/README.md).',
  );
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
