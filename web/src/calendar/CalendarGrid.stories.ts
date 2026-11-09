import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import CalendarGrid from './CalendarGrid.vue';
import { monthWeeks, weekDays } from './model';
import { sampleItem } from './samples';

// The late-night post of the spec: 2026-11-08T23:30:00Z is shown in Europe/Berlin as Monday 9 November, 00:30.
const lateNight = sampleItem({
  id: 'late',
  title: 'Späte Folge',
  status: 'scheduled',
  startsAt: '2026-11-08T23:30:00Z',
  localDate: '2026-11-09',
  localTime: '00:30',
});
const evening = sampleItem({ id: 'evening', title: 'Abendfolge', localDate: '2026-11-11', localTime: '18:00', status: 'draft' });

const meta = {
  title: 'Calendar/CalendarGrid',
  component: CalendarGrid,
  args: {
    weeks: monthWeeks('2026-11-01', '2026-11-30'),
    items: [lateNight, evening],
    selectedId: null,
    onSelect: fn(),
  },
} satisfies Meta<typeof CalendarGrid>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Month: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const monday = canvas.getByRole('group', { name: 'Montag, 9. November 2026' });
    expect(within(monday).getByRole('button', { name: /00:30.*Späte Folge.*Geplant/ })).toBeVisible();
    expect(canvas.getByRole('group', { name: 'Mittwoch, 11. November 2026' })).toBeVisible();

    await userEvent.click(within(monday).getByRole('button', { name: /Späte Folge/ }));
    expect(args.onSelect).toHaveBeenCalledWith('late');
  },
};

export const LateNightStaysOnMonday: Story = {
  args: { items: [lateNight], weeks: [weekDays('2026-11-09')] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('group', { name: 'Montag, 9. November 2026' })).toHaveTextContent('00:30');
    expect(canvas.queryByRole('group', { name: 'Sonntag, 8. November 2026' })).toBeNull();
  },
};

export const SelectedEntry: Story = {
  args: { selectedId: 'evening' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('button', { name: /Abendfolge/ })).toHaveAttribute('aria-pressed', 'true');
  },
};
