import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PoolList from './PoolList.vue';
import type { PoolItem } from './pool';

const RAW: PoolItem = {
  id: '4f0c7a52-9d1e-4b8a-8e3f-2a6c5d7b9e10',
  kind: 'raw',
  stage: 'raw',
  status: 'active',
  uploaderId: '0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d',
  title: 'Coop-Abend',
  rawItemId: null,
  assetId: null,
  startSeconds: null,
  endSeconds: null,
  usage: null,
  personTags: [],
  freeTags: ['coop'],
  publishedOn: [],
  createdAt: '2026-11-01T20:00:00Z',
};

const CLIP: PoolItem = {
  ...RAW,
  id: '5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d',
  kind: 'clip',
  stage: 'cut',
  status: 'suggested',
  title: 'Boss-Kampf',
  rawItemId: RAW.id,
  startSeconds: 120,
  endSeconds: 180,
  usage: 'never_used',
  freeTags: ['speedrun'],
};

const PUBLISHED: PoolItem = {
  ...CLIP,
  id: '6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d9e',
  stage: 'rendered',
  status: 'active',
  title: 'Boss-Kampf Short',
  usage: 'published',
  publishedOn: [{ channelId: '7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d9e0f', channelName: 'Twitch Hauptkanal' }],
};

const meta = {
  title: 'Library/PoolList',
  component: PoolList,
  args: {
    items: [RAW, CLIP],
    status: 'ready',
    hasMore: true,
    onMore: fn(),
    onRetry: fn(),
  },
} satisfies Meta<typeof PoolList>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Items: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
    expect(canvas.getByText('Coop-Abend')).toBeVisible();
    expect(canvas.getByText('Boss-Kampf')).toBeVisible();
    expect(canvas.getByText('Vorschlag')).toBeVisible();
    expect(canvas.getByText('Nie verwendet')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Mehr laden' }));
    expect(args.onMore).toHaveBeenCalledTimes(1);
  },
};

export const PublishedOnChannel: Story = {
  args: { items: [PUBLISHED] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Boss-Kampf Short')).toBeVisible();
    expect(canvas.getByText('Veröffentlicht auf Twitch Hauptkanal')).toBeVisible();
    expect(canvas.queryByText('Nie verwendet')).toBeNull();
  },
};

export const Empty: Story = {
  args: { items: [], hasMore: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Keine Einträge gefunden.')).toBeVisible();
    expect(canvas.queryByRole('button', { name: 'Mehr laden' })).toBeNull();
  },
};

export const LoadFailed: Story = {
  args: { items: [], status: 'error' },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};

export const EditorTranscriptAction: Story = {
  args: { canEdit: true, onTranscript: fn() },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    // Only the raw recording offers a transcript; the clip row has no such action.
    expect(canvas.getAllByRole('button', { name: 'Transkript' })).toHaveLength(1);
    await userEvent.click(canvas.getByRole('button', { name: 'Transkript' }));
    expect(args.onTranscript).toHaveBeenCalledWith(RAW.id);
  },
};

export const ViewerHasNoUploadActions: Story = {
  args: { canEdit: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button', { name: 'Transkript' })).toBeNull();
  },
};
