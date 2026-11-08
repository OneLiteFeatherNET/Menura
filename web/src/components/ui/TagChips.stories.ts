import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import TagChips from './TagChips.vue';

const meta = {
  title: 'UI/TagChips',
  component: TagChips,
  args: {
    tags: ['Gaming', 'Speedrun', 'Deutsch'],
    removable: false,
    'onRemove': fn(),
  },
} satisfies Meta<typeof TagChips>;
export default meta;

type Story = StoryObj<typeof meta>;

/** Viewer: the tags are shown and cannot be removed. */
export const ViewerReadOnly: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Gaming')).toBeVisible();
    expect(canvas.queryByRole('button')).toBeNull();
  },
};

/** Editor: each tag has a button that removes it, named after the tag. */
export const EditorRemovable: Story = {
  args: { removable: true },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Entfernen: Speedrun' }));
    expect(args.onRemove).toHaveBeenCalledWith('Speedrun');
  },
};

export const Empty: Story = {
  args: { tags: [], emptyText: 'Keine Schlagwörter vergeben.' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Keine Schlagwörter vergeben.')).toBeVisible();
  },
};

export const LongTag: Story = {
  args: {
    tags: ['Ein-sehr-langer-Schlagwort-ohne-Leerzeichen-der-umbrechen-muss-damit-er-passt'],
    removable: true,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText(/Ein-sehr-langer/)).toBeVisible();
  },
};
