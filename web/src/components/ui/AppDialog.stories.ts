import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, waitFor, within } from 'storybook/test';
import AppDialog from './AppDialog.vue';

const meta = {
  title: 'UI/AppDialog',
  component: AppDialog,
  args: {
    title: 'Ausschnitt planen',
    open: true,
    'onClose': fn(),
  },
} satisfies Meta<typeof AppDialog>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Open: Story = {
  render: (args) => ({
    components: { AppDialog },
    setup: () => ({ args }),
    template: '<AppDialog v-bind="args"><p>Datum und Uhrzeit wählen.</p></AppDialog>',
  }),
  play: async ({ canvasElement, args }) => {
    const dialog = within(document.body).getByRole('dialog', { name: 'Ausschnitt planen' });
    expect(dialog).toBeVisible();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Schließen' }));
    // The native close event is queued after the click, so the report is awaited instead of read at once.
    await waitFor(() => expect(args.onClose).toHaveBeenCalledTimes(1));
    expect(canvasElement).toBeTruthy();
  },
};

export const Closed: Story = {
  args: { open: false },
  render: (args) => ({
    components: { AppDialog },
    setup: () => ({ args }),
    template: '<AppDialog v-bind="args"><p>Verborgen.</p></AppDialog>',
  }),
  play: async () => {
    expect(within(document.body).queryByRole('dialog')).toBeNull();
  },
};

export const LongContent: Story = {
  render: (args) => ({
    components: { AppDialog },
    setup: () => ({ args, lines: Array.from({ length: 30 }, (_, i) => `Zeile ${i + 1}`) }),
    template: '<AppDialog v-bind="args"><p v-for="line in lines" :key="line">{{ line }}</p></AppDialog>',
  }),
  play: async () => {
    const dialog = within(document.body).getByRole('dialog', { name: 'Ausschnitt planen' });
    expect(dialog).toBeVisible();
  },
};
