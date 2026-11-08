import type { StorybookConfig } from '@storybook/vue3-vite';

// Stories live next to the shared components. The Vite configuration is read from web/vite.config.ts, so a
// component renders here with the same plugins as in the app.
const config: StorybookConfig = {
  stories: ['../src/components/ui/**/*.stories.ts'],
  addons: ['@storybook/addon-a11y', '@storybook/addon-vitest'],
  framework: { name: '@storybook/vue3-vite', options: {} },
};

export default config;
