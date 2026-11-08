import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, within } from 'storybook/test';
import { sampleSeries, sampleSeriesWithoutEngagement } from '../../analytics/samples';
import AnalyticsSeriesPanel from './AnalyticsSeriesPanel.vue';

const meta = {
  title: 'Analytics/SeriesPanel',
  component: AnalyticsSeriesPanel,
  args: {
    status: 'ready',
    series: sampleSeries().series,
    rangeLabel: '11.08.2026 bis 08.11.2026',
    'onRetry': fn(),
  },
} satisfies Meta<typeof AnalyticsSeriesPanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const RankedByEngagedViews: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 2, name: 'Serien' })).toBeVisible();
    const rows = canvas.getAllByRole('row');
    expect(rows[1], 'the series with the most engaged views comes first').toHaveTextContent('Kubernetes in 30 s');
    expect(rows[2], 'the next series follows').toHaveTextContent('Deployments in 30 s');
    expect(rows[3], 'the posts in no series come last').toHaveTextContent('Ohne Serie');
  },
};

export const WithoutEngagement: Story = {
  args: { series: sampleSeriesWithoutEngagement().series },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getAllByText('nicht verfügbar'), 'engaged views and average are not available').toHaveLength(2);
  },
};

export const NoPosts: Story = {
  args: { series: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Noch keine Kennzahlen')).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading', series: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt …');
  },
};
