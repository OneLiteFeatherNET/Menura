import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import CalendarList from './CalendarList.vue';
import { listGroups } from './model';
import { CHANNEL_ID, sampleItem } from './samples';

const groups = listGroups({
  items: [
    sampleItem({ id: 'late', title: 'Späte Folge', localDate: '2026-11-09', localTime: '00:30', status: 'scheduled' }),
    sampleItem({ id: 'noon', title: 'Mittagsclip', localDate: '2026-11-10', localTime: '12:00', status: 'draft' }),
    sampleItem({
      id: 'evening',
      title: 'Abendfolge',
      localDate: '2026-11-10',
      localTime: '18:00',
      status: 'published',
      channelId: CHANNEL_ID,
      channelName: 'OneLiteFeather',
    }),
  ],
});

const meta = {
  title: 'Calendar/CalendarList',
  component: CalendarList,
  args: { groups, selectedId: null, onSelect: fn() },
} satisfies Meta<typeof CalendarList>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { name: 'Dienstag, 10. November 2026' })).toBeVisible();
    expect(canvas.getByRole('heading', { name: 'Montag, 9. November 2026' })).toBeVisible();

    await userEvent.click(canvas.getByRole('button', { name: /18:00.*Abendfolge.*OneLiteFeather.*Veröffentlicht/ }));
    expect(args.onSelect).toHaveBeenCalledWith('evening');
  },
};

export const Empty: Story = {
  args: { groups: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Im gewählten Zeitraum gibt es keine Beiträge.')).toBeVisible();
  },
};
