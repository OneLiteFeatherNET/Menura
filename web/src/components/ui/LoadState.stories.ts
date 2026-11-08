import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import LoadState from './LoadState.vue';

const meta = {
  title: 'UI/LoadState',
  component: LoadState,
  args: {
    status: 'ready',
    loadingLabel: 'Lädt …',
    errorMessage: 'Die Einträge konnten nicht geladen werden.',
    emptyText: 'Keine Einträge gefunden.',
    retryLabel: 'Erneut versuchen',
    'onRetry': fn(),
  },
} satisfies Meta<typeof LoadState>;
export default meta;

type Story = StoryObj<typeof meta>;

const content = (args: Record<string, unknown>) => ({
  components: { LoadState },
  setup: () => ({ args }),
  template: '<LoadState v-bind="args"><p>Inhalt der Liste</p></LoadState>',
});

export const Ready: Story = {
  render: content,
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Inhalt der Liste')).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading' },
  render: content,
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt …');
  },
};

export const Failed: Story = {
  args: { status: 'error' },
  render: content,
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Die Einträge konnten nicht geladen werden.');
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};

export const Empty: Story = {
  args: { status: 'empty' },
  render: content,
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Keine Einträge gefunden.')).toBeVisible();
  },
};
