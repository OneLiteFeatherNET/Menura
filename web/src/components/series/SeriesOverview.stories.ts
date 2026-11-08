import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import SeriesOverview from './SeriesOverview.vue';
import { sampleOverview, sampleSeries } from './seriesSamples';

const meta = {
  title: 'Series/SeriesOverview',
  component: SeriesOverview,
  args: {
    status: 'ready',
    overview: sampleOverview(),
    'onRetry': fn(),
    'onOpen': fn(),
  },
} satisfies Meta<typeof SeriesOverview>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('table', { name: 'Serien' })).toBeVisible();
    expect(canvas.getByText('#kubernetes30s')).toBeVisible();
    expect(canvas.getByText('80 % · 8 von 10 zugeordnet')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Kubernetes in 30 s' }));
    expect(args.onOpen).toHaveBeenCalledWith('a1b2c3d4-0000-4000-8000-000000000001');
  },
};

export const WithoutPlayersSeries: Story = {
  args: {
    overview: sampleOverview({ warnings: ['no_players_series'] }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Keine aktive Serie richtet sich an Spieler');
  },
};

export const FlaggedWeek: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getAllByText('unter 90 %')).toHaveLength(1);
    expect(canvas.getByText('Keine Shorts')).toBeVisible();
  },
};

export const WithoutSeries: Story = {
  args: {
    overview: sampleOverview({ series: [], warnings: ['no_players_series'] }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Noch keine Serie angelegt.')).toBeVisible();
    expect(canvas.getByRole('alert')).toBeVisible();
  },
};

export const WithInactiveSeries: Story = {
  args: {
    overview: sampleOverview({ series: [sampleSeries({ active: false, name: 'Pausiert' })] }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('inaktiv')).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading', overview: null },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Lädt …')).toBeVisible();
  },
};

export const Failed: Story = {
  args: { status: 'error', overview: null },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Serien konnten nicht geladen werden.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalled();
  },
};
