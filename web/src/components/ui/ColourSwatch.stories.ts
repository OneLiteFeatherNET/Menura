import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import ColourSwatch from './ColourSwatch.vue';

const meta = {
  title: 'UI/ColourSwatch',
  component: ColourSwatch,
  args: { name: 'primary', light: '#6638e4', dark: '#ccbdff' },
} satisfies Meta<typeof ColourSwatch>;
export default meta;

type Story = StoryObj<typeof meta>;

export const LightAndDark: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('primary')).toBeVisible();
    expect(canvas.getByText('Hell #6638e4')).toBeVisible();
    expect(canvas.getByText('Dunkel #ccbdff')).toBeVisible();
  },
};

export const LongTokenName: Story = {
  args: { name: 'inverse-on-surface-container-with-a-very-long-name', light: '#f4eff4', dark: '#313033' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/inverse-on-surface-container/)).toBeVisible();
  },
};
