import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, within } from 'storybook/test';
import ExternalPostOverview from './ExternalPostOverview.vue';
import type { PostRow } from '../../api/externalPosts';

const rows: PostRow[] = [
  {
    id: 'p1',
    title: 'Morgenstream Highlights',
    format: 'video',
    publishedAt: '2026-11-01T18:00:00Z',
    status: 'Live',
    link: 'https://video.example.test/p1',
  },
  {
    id: 'p2',
    title: 'Speedrun-Versuch 3',
    format: 'stream',
    publishedAt: '2026-10-20T19:30:00Z',
    status: 'Entfernt',
    link: null,
  },
];

const meta = {
  title: 'Channels/ExternalPostOverview',
  component: ExternalPostOverview,
  args: {
    channelName: 'OLF Speedruns',
    channelState: 'connected',
    lastSuccessfulSyncAt: '2026-11-08T06:17:00Z',
    status: 'ready',
    rows,
    'onRetry': fn(),
  },
} satisfies Meta<typeof ExternalPostOverview>;
export default meta;

type Story = StoryObj<typeof meta>;

/** A viewer sees the posts with their status; a removed post keeps its title but offers no link and no edit control. */
export const Overview: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('table', { name: 'Beiträge des Kanals' })).toBeVisible();
    expect(canvas.getByRole('link', { name: 'Morgenstream Highlights' })).toBeVisible();
    expect(canvas.getByText('Speedrun-Versuch 3')).toBeVisible();
    expect(canvas.queryByRole('link', { name: 'Speedrun-Versuch 3' })).toBeNull();
    expect(canvas.queryByRole('button')).toBeNull();
  },
};

/** A channel that needs reconnection keeps its posts and says that the import waits. */
export const WaitingForReconnection: Story = {
  args: { channelState: 'needs_reconnection' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText(/wartet auf die Neuverbindung/)).toBeVisible();
    expect(canvas.getByText('Speedrun-Versuch 3')).toBeVisible();
  },
};

export const NeverSynced: Story = {
  args: { lastSuccessfulSyncAt: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Noch nie vollständig synchronisiert.')).toBeVisible();
  },
};

export const Empty: Story = {
  args: { status: 'empty', rows: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Keine Beiträge.')).toBeVisible();
  },
};

/** A failed read offers a retry, the only control of the view. */
export const LoadFailed: Story = {
  args: { status: 'error', rows: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toBeVisible();
    expect(canvas.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible();
  },
};
