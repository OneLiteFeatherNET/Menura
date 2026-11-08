import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import SeriesDetail from './SeriesDetail.vue';
import { sampleDetail } from './seriesSamples';

const meta = {
  title: 'Series/SeriesDetail',
  component: SeriesDetail,
  args: {
    status: 'ready',
    detail: sampleDetail(),
    'onRetry': fn(),
    'onBack': fn(),
  },
} satisfies Meta<typeof SeriesDetail>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { name: 'Kubernetes in 30 s' })).toBeVisible();
    expect(canvas.getByText('Kurz erklärt (de)')).toBeVisible();
    expect(canvas.getByText('Pods in 30 Sekunden')).toBeVisible();
    expect(canvas.getByText('Fehlt in der Playlist')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Zurück zur Übersicht' }));
    expect(args.onBack).toHaveBeenCalled();
  },
};

export const WithoutEpisodes: Story = {
  args: {
    detail: { series: sampleDetail().series, episodes: [] },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Noch keine Folge zugeordnet.')).toBeVisible();
  },
};

export const Failed: Story = {
  args: { status: 'error', detail: null },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Serie konnte nicht geladen werden.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalled();
  },
};
