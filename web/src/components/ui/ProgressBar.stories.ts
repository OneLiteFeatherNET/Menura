import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import ProgressBar from './ProgressBar.vue';

const meta = {
  title: 'UI/ProgressBar',
  component: ProgressBar,
  args: { value: 40, label: 'Hochgeladen' },
} satisfies Meta<typeof ProgressBar>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Partial: Story = {
  play: async ({ canvasElement }) => {
    const bar = within(canvasElement).getByRole('progressbar', { name: 'Hochgeladen' });
    expect(bar).toHaveAttribute('aria-valuenow', '40');
  },
};

export const OutOfRangeIsClamped: Story = {
  args: { value: 140 },
  play: async ({ canvasElement }) => {
    const bar = within(canvasElement).getByRole('progressbar', { name: 'Hochgeladen' });
    expect(bar).toHaveAttribute('aria-valuenow', '100');
  },
};
