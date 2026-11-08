import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PostRevisions from './PostRevisions.vue';
import { sampleRevision } from './samples';

const meta = {
  title: 'Calendar/PostRevisions',
  component: PostRevisions,
  args: {
    revisions: [sampleRevision()],
    status: 'ready',
    canDecide: true,
    busy: false,
    onAccept: fn(),
    onReject: fn(),
    onRetry: fn(),
  },
} satisfies Meta<typeof PostRevisions>;
export default meta;

type Story = StoryObj<typeof meta>;

export const PublisherDecidesPending: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Offen')).toBeVisible();
    expect(canvas.getByText('Termin')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Ablehnen' }));
    expect(args.onReject).toHaveBeenCalledWith('r0000000-0000-4000-8000-000000000001');
  },
};

export const EditorReadsOnly: Story = {
  args: { canDecide: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button', { name: 'Annehmen' })).toBeNull();
    expect(canvas.getByText('Offene Vorschläge werden von einer veröffentlichenden Person entschieden.')).toBeVisible();
  },
};

export const Empty: Story = {
  args: { revisions: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Zu diesem Beitrag gibt es keine Vorschläge.')).toBeVisible();
  },
};

export const LoadFailed: Story = {
  args: { revisions: [], status: 'error' },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Vorschläge konnten nicht geladen werden.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalled();
  },
};
