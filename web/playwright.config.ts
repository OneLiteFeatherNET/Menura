import { defineConfig, devices } from '@playwright/test';

// End-to-end tests drive the built web app and the real server in environment e2e. Chromium only. The server is
// started first (it needs its PostgreSQL container); `./gradlew :server:installDist` must have run before.
// Traces are kept for every failed test; in CI a failed test is retried once and reported as flaky if it passes.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'node e2e/support/run-server.mjs',
      // The readiness port opens after the health check and the test-only seed (e2e/support/run-server.mjs).
      url: 'http://localhost:8081/ready',
      timeout: 300_000,
      reuseExistingServer: false,
    },
    {
      command:
        'pnpm exec vite build --mode e2e --outDir dist-e2e && pnpm exec vite preview --mode e2e --outDir dist-e2e --port 4173 --strictPort',
      url: 'http://localhost:4173',
      timeout: 180_000,
      reuseExistingServer: false,
    },
  ],
});
