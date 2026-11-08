import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import { sampleAggregate, sampleChannels } from '../../analytics/samples';
import AnalyticsChannelsPanel from './AnalyticsChannelsPanel.vue';

const meta = {
  title: 'Analytics/ChannelsPanel',
  component: AnalyticsChannelsPanel,
  args: {
    status: 'ready',
    channels: sampleChannels(),
    rangeLabel: '11.08.2026 bis 08.11.2026',
    'onRetry': fn(),
  },
} satisfies Meta<typeof AnalyticsChannelsPanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const SeveralChannels: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 2, name: 'Kanäle' })).toBeVisible();
    expect(canvas.getByRole('table', { name: 'Kennzahlen je Kanal' })).toBeVisible();
    expect(canvas.getByRole('img', { name: 'Aufrufe je Kanal: OneLiteFeather 4.200; Menura Clips 980' })).toBeVisible();
    expect(canvas.getAllByText('nicht verfügbar').length, 'the missing values read as not available').toBeGreaterThan(0);
  },
};

export const OneChannel: Story = {
  args: { channels: [sampleAggregate('c-1', 'OneLiteFeather', { views: 4200 }, 12)] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('img', { name: 'Aufrufe je Kanal: OneLiteFeather 4.200' })).toBeVisible();
  },
};

export const NoPosts: Story = {
  args: { channels: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Noch keine Kennzahlen')).toBeVisible();
    expect(canvas.queryByRole('table')).toBeNull();
  },
};

export const Loading: Story = {
  args: { status: 'loading', channels: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt …');
  },
};

export const Failed: Story = {
  args: { status: 'error', channels: null, errorMessage: 'Die Analytics konnten nicht geladen werden.' },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};
