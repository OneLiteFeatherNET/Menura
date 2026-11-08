import type { Preview } from '@storybook/vue3-vite';
import '@material/web/divider/divider.js';
import '../src/theme/tokens.css';
import '../src/style.css';
import { expectNoSeriousA11yViolations } from './a11y';

// Every story is an accessibility test: axe runs on the rendered story after its play function. Serious and critical
// violations fail the story; moderate and minor ones are reported and do not fail the run.
export const afterEach = async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  await expectNoSeriousA11yViolations(canvasElement);
};

// Light and dark mode switch the same data-theme attribute that the app uses for its tokens. The locale is German,
// as in the app.
const preview: Preview = {
  globalTypes: {
    theme: {
      description: 'Theme of the tokens',
      defaultValue: 'light',
      toolbar: {
        title: 'Theme',
        icon: 'mirror',
        items: [
          { value: 'light', title: 'Hell' },
          { value: 'dark', title: 'Dunkel' },
        ],
        dynamicTitle: true,
      },
    },
  },
  decorators: [
    (story, context) => {
      document.documentElement.dataset.theme = context.globals.theme ?? 'light';
      document.documentElement.lang = 'de';
      return story();
    },
  ],
  parameters: {
    layout: 'padded',
    // Violations are shown in the addon panel. The failing rule (serious and critical) is applied by the test setup.
    a11y: { test: 'todo' },
  },
};

export default preview;
