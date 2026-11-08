import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import { page } from 'vitest/browser';
import '@material/web/button/filled-button.js';
import '@material/web/button/outlined-button.js';
import PageHeader from './PageHeader.vue';

const LONG_TITLE =
  'Pool-Eintrag Stream-Ausschnitt vom Montagabend mit sehr langem Titel, der über mehrere Zeilen umbricht';

const meta = {
  title: 'UI/PageHeader',
  component: PageHeader,
  args: {
    title: 'Pool',
    subtitle: 'Aufnahmen und Ausschnitte, die noch nicht geplant sind.',
  },
} satisfies Meta<typeof PageHeader>;
export default meta;

type Story = StoryObj<typeof meta>;

/** The viewport widths the spec names; the document must not scroll sideways at either one. */
const DESKTOP_WIDTH = 1280;
const PHONE_WIDTH = 390;

/** The Material buttons keep their real button in a shadow root, so the action is found by its element name. */
function findAction(canvasElement: HTMLElement, label: string): HTMLElement {
  const action = [...canvasElement.querySelectorAll<HTMLElement>('md-filled-button, md-outlined-button')].find(
    (el) => el.textContent?.trim() === label,
  );
  if (!action) throw new Error(`action "${label}" is not rendered`);
  return action;
}

async function expectActionInsideViewport(canvasElement: HTMLElement, label: string, width: number) {
  await page.viewport(width, 900);
  const right = findAction(canvasElement, label).getBoundingClientRect().right;
  expect(right, `action right edge at ${width} px`).toBeLessThanOrEqual(width);
  expect(document.documentElement.scrollWidth, `no horizontal scroll at ${width} px`).toBeLessThanOrEqual(width);
}

export const Default: Story = {
  render: (args) => ({
    components: { PageHeader },
    setup: () => ({ args }),
    template: '<PageHeader v-bind="args" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 1, name: 'Pool' })).toBeVisible();
    expect(canvas.getByText('Aufnahmen und Ausschnitte, die noch nicht geplant sind.')).toBeVisible();
  },
};

/** Viewer: the header carries no action at all, so there is nothing to click. */
export const ViewerWithoutActions: Story = {
  render: (args) => ({
    components: { PageHeader },
    setup: () => ({ args }),
    template: '<PageHeader v-bind="args" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button')).toBeNull();
  },
};

/** Editor: the primary action is part of the header. */
export const EditorWithAction: Story = {
  render: (args) => ({
    components: { PageHeader },
    setup: () => ({ args }),
    template: '<PageHeader v-bind="args"><md-filled-button>Neuer Eintrag</md-filled-button></PageHeader>',
  }),
  play: async ({ canvasElement }) => {
    const action = findAction(canvasElement, 'Neuer Eintrag');
    expect(action.getBoundingClientRect().width, 'the action has a visible box').toBeGreaterThan(0);
  },
};

/** A long title with a primary action and a secondary one, checked at desktop width and at phone width. */
export const LongTitleWithActionsGeometry: Story = {
  args: { title: LONG_TITLE, subtitle: undefined },
  render: (args) => ({
    components: { PageHeader },
    setup: () => ({ args }),
    template:
      '<PageHeader v-bind="args"><md-outlined-button>Filtern</md-outlined-button><md-filled-button>Ausschnitt planen</md-filled-button></PageHeader>',
  }),
  play: async ({ canvasElement }) => {
    await expectActionInsideViewport(canvasElement, 'Ausschnitt planen', DESKTOP_WIDTH);
    await expectActionInsideViewport(canvasElement, 'Ausschnitt planen', PHONE_WIDTH);
  },
};
