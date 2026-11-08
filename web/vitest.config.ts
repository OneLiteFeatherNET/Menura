import { defineConfig, mergeConfig } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';
import { storybookTest } from '@storybook/addon-vitest/vitest-plugin';
import viteConfig from './vite.config.ts';

// The unit project runs the pure logic and the app shell in jsdom. The storybook project runs every story's play
// function in Chromium through the Playwright provider, with the same Vite configuration as the app and Storybook.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      projects: [
        {
          extends: true,
          test: {
            name: 'unit',
            environment: 'jsdom',
            include: ['tests/**/*.test.ts'],
          },
        },
        {
          extends: true,
          plugins: [storybookTest({ configDir: new URL('./.storybook', import.meta.url).pathname })],
          test: {
            name: 'storybook',
            browser: {
              enabled: true,
              headless: true,
              provider: playwright(),
              instances: [{ browser: 'chromium' }],
            },
          },
        },
      ],
    },
  }),
);
