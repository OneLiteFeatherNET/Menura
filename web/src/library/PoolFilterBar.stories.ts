import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PoolFilterBar from './PoolFilterBar.vue';
import { EMPTY_FILTERS } from './pool';

const meta = {
  title: 'Library/PoolFilterBar',
  component: PoolFilterBar,
  args: {
    filters: EMPTY_FILTERS,
    'onUpdate:filters': fn(),
  },
} satisfies Meta<typeof PoolFilterBar>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('search', { name: 'Pool filtern' })).toBeVisible();
    expect(canvas.queryByRole('button', { name: 'Filter zurücksetzen' })).toBeNull();
    await userEvent.type(canvas.getByRole('searchbox', { name: 'Tag' }), 's');
    expect(args['onUpdate:filters']).toHaveBeenCalledWith({ ...EMPTY_FILTERS, tag: 's' });
  },
};

export const Chosen: Story = {
  args: { filters: { mine: true, tag: 'speedrun', stage: 'cut', usage: '' } },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Filter zurücksetzen' }));
    expect(args['onUpdate:filters']).toHaveBeenCalledWith(EMPTY_FILTERS);
  },
};
