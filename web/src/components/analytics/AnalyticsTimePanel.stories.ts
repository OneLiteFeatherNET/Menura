import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import { sampleEqualAge } from '../../analytics/samples';
import AnalyticsTimePanel from './AnalyticsTimePanel.vue';

const meta = {
  title: 'Analytics/TimePanel',
  component: AnalyticsTimePanel,
  args: {
    status: 'ready',
    equalAge: sampleEqualAge('hour'),
    choice: { by: 'hour', offsetHours: 24, metric: 'views' },
    'onRetry': fn(),
    'onChange': fn(),
  },
} satisfies Meta<typeof AnalyticsTimePanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const ByHour: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 2, name: 'Uhrzeit/Wochentag' })).toBeVisible();
    expect(canvas.getByRole('combobox', { name: 'Abstand zur Veröffentlichung' })).toHaveTextContent('+24 h');
    expect(canvas.getByText('09 Uhr')).toBeVisible();
  },
};

export const ByWeekday: Story = {
  args: { equalAge: sampleEqualAge('weekday'), choice: { by: 'weekday', offsetHours: 168, metric: 'views' } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Abstand zur Veröffentlichung' })).toHaveTextContent('+7 Tage');
    expect(canvas.getByText('Montag')).toBeVisible();
  },
};

export const ChooseOffset: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Abstand zur Veröffentlichung' }));
    await userEvent.click(canvas.getByRole('option', { name: '+7 Tage' }));
    expect(args.onChange).toHaveBeenCalledWith({ by: 'hour', offsetHours: 168, metric: 'views' });
  },
};

export const NoPosts: Story = {
  args: {
    equalAge: {
      ...sampleEqualAge('hour'),
      slots: sampleEqualAge('hour').slots.map((slot) => ({ ...slot, posts: 0, median: null })),
    },
  },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Noch keine Kennzahlen')).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading', equalAge: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt …');
  },
};
