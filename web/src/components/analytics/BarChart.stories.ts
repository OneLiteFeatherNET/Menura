import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import BarChart from './BarChart.vue';

const meta = {
  title: 'Analytics/BarChart',
  component: BarChart,
  args: {
    caption: 'Aufrufe je Kanal',
    items: [],
  },
} satisfies Meta<typeof BarChart>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Empty: Story = {
  args: { items: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Keine Daten.')).toBeVisible();
    expect(canvas.queryByRole('img')).toBeNull();
  },
};

export const OneChannel: Story = {
  args: { items: [{ label: 'OneLiteFeather', value: 4200 }] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('img', { name: 'Aufrufe je Kanal: OneLiteFeather 4.200' })).toBeVisible();
    expect(canvas.getByText('4.200')).toBeVisible();
    expect(canvasElement.querySelectorAll('.bar-chart__bar')).toHaveLength(1);
  },
};

export const SeveralChannels: Story = {
  args: {
    items: [
      { label: 'OneLiteFeather', value: 4200 },
      { label: 'Menura Clips', value: 980 },
      { label: 'Gaming-Kanal', value: 2100 },
    ],
  },
  play: async ({ canvasElement }) => {
    const bars = [...canvasElement.querySelectorAll<HTMLElement>('.bar-chart__bar')];
    expect(bars, 'one bar per channel').toHaveLength(3);
    expect(bars[0]?.style.width, 'the largest value fills the track').toBe('100%');
    const share = parseFloat(bars[1]?.style.width ?? '');
    expect(share, 'a smaller value gets a smaller share of the track').toBeGreaterThan(20);
    expect(share, 'a smaller value gets a smaller share of the track').toBeLessThan(30);
  },
};

export const NotSupportedMetric: Story = {
  args: {
    items: [
      { label: 'Menura Clips', value: null },
      { label: 'OneLiteFeather', value: 980 },
    ],
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('nicht verfügbar')).toBeVisible();
    expect(canvasElement.querySelectorAll('.bar-chart__bar'), 'no bar for the missing value').toHaveLength(1);
  },
};
