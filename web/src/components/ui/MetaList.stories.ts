import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import MetaList from './MetaList.vue';

const meta = {
  title: 'UI/MetaList',
  component: MetaList,
  args: {
    items: [
      { label: 'Quelle', value: 'Aufnahme vom 3. November' },
      { label: 'Dauer', value: '00:42' },
      { label: 'Format', value: '9:16' },
    ],
  },
} satisfies Meta<typeof MetaList>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Quelle')).toBeVisible();
    expect(canvas.getByText('00:42')).toBeVisible();
  },
};

export const Empty: Story = {
  args: { items: [], emptyText: 'Keine Angaben vorhanden.' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Keine Angaben vorhanden.')).toBeVisible();
  },
};

export const LongValue: Story = {
  args: {
    items: [
      {
        label: 'Beschreibung',
        value: 'Ein sehr langer Wert ohne Leerzeichen: https://example.invalid/aufnahme/abcdefghijklmnopqrstuvwxyz0123456789',
      },
    ],
  },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/sehr langer Wert/)).toBeVisible();
  },
};
