import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import StatusBadge from './StatusBadge.vue';

const meta = {
  title: 'UI/StatusBadge',
  component: StatusBadge,
  args: { label: 'Geplant', tone: 'info' },
} satisfies Meta<typeof StatusBadge>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Info: Story = {
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Geplant')).toBeVisible();
  },
};

export const Neutral: Story = { args: { label: 'Entwurf', tone: 'neutral' } };
export const Success: Story = { args: { label: 'Veröffentlicht', tone: 'success' } };
export const Warning: Story = { args: { label: 'Verbindung läuft ab', tone: 'warning' } };
export const Error: Story = { args: { label: 'Fehlgeschlagen', tone: 'error' } };

export const LongLabel: Story = {
  args: { label: 'Ein sehr langer Statustext, der im Abzeichen umbricht und lesbar bleibt', tone: 'neutral' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/sehr langer Statustext/)).toBeVisible();
  },
};
