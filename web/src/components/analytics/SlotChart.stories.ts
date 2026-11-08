import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import { sampleEqualAge, sampleEqualAgeNotSupported } from '../../analytics/samples';
import SlotChart from './SlotChart.vue';

const meta = {
  title: 'Analytics/SlotChart',
  component: SlotChart,
  args: {
    caption: 'Median je Zeitfenster',
    by: 'hour',
    slots: sampleEqualAge('hour').slots,
  },
} satisfies Meta<typeof SlotChart>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Empty: Story = {
  args: { slots: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Keine Daten.')).toBeVisible();
  },
};

export const NoPostsInSlots: Story = {
  args: {
    by: 'weekday',
    slots: sampleEqualAge('weekday').slots.map((slot) => ({ ...slot, posts: 0, median: null })),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvasElement.querySelectorAll('.slot-chart__column'), 'one column per weekday').toHaveLength(7);
    expect(canvas.queryByText('Die Plattform liefert diese Kennzahl für die Beiträge nicht.')).toBeNull();
  },
};

export const HourlyDay: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvasElement.querySelectorAll('.slot-chart__column'), 'one column per hour').toHaveLength(24);
    expect(canvas.getByText('09 Uhr')).toBeVisible();
    expect(canvasElement.querySelectorAll('.slot-chart__bar'), 'bars only where a median exists').toHaveLength(11);
  },
};

export const Weekdays: Story = {
  args: { by: 'weekday', slots: sampleEqualAge('weekday').slots },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvasElement.querySelectorAll('.slot-chart__column'), 'one column per weekday').toHaveLength(7);
    expect(canvas.getByText('Montag')).toBeVisible();
  },
};

export const NotSupportedMetric: Story = {
  args: { by: 'weekday', slots: sampleEqualAgeNotSupported('weekday').slots },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Plattform liefert diese Kennzahl für die Beiträge nicht.')).toBeVisible();
    expect(canvas.getAllByText('–'), 'every slot shows a dash instead of a value').toHaveLength(7);
    expect(canvasElement.querySelectorAll('.slot-chart__bar'), 'no bar without a median').toHaveLength(0);
  },
};
