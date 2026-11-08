import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import ConfirmInline from './ConfirmInline.vue';

const meta = {
  title: 'UI/ConfirmInline',
  component: ConfirmInline,
  args: {
    message: 'Eintrag wirklich aus dem Pool entfernen?',
    confirmLabel: 'Entfernen',
    cancelLabel: 'Abbrechen',
    tone: 'default',
    busy: false,
    'onConfirm': fn(),
    'onCancel': fn(),
  },
} satisfies Meta<typeof ConfirmInline>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Entfernen' }));
    expect(args.onConfirm).toHaveBeenCalledTimes(1);
    await userEvent.click(canvas.getByRole('button', { name: 'Abbrechen' }));
    expect(args.onCancel).toHaveBeenCalledTimes(1);
  },
};

export const Danger: Story = {
  args: { tone: 'danger', confirmLabel: 'Endgültig löschen' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('button', { name: 'Endgültig löschen' })).toBeVisible();
  },
};

export const Busy: Story = {
  args: { busy: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('button', { name: 'Entfernen' })).toBeDisabled();
    expect(canvas.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
  },
};

export const LongMessage: Story = {
  args: {
    message:
      'Dieser Ausschnitt ist bereits auf drei Kanälen geplant. Beim Entfernen werden alle geplanten Veröffentlichungen gelöscht, und dieser Vorgang kann nicht rückgängig gemacht werden.',
  },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/kann nicht rückgängig gemacht werden/)).toBeVisible();
  },
};
