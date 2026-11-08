import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import CalendarToolbar from './CalendarToolbar.vue';

const meta = {
  title: 'Calendar/CalendarToolbar',
  component: CalendarToolbar,
  args: {
    view: 'month',
    label: 'November 2026',
    canPlan: true,
    'onUpdate:view': fn(),
    onPrevious: fn(),
    onNext: fn(),
    onToday: fn(),
    onPlan: fn(),
  },
} satisfies Meta<typeof CalendarToolbar>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Month: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('November 2026')).toBeVisible();
    expect(canvas.getByRole('button', { name: 'Monat' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(canvas.getByRole('button', { name: 'Woche' }));
    expect(args['onUpdate:view']).toHaveBeenCalledWith('week');
    await userEvent.click(canvas.getByRole('button', { name: 'Nächster Zeitraum' }));
    expect(args.onNext).toHaveBeenCalled();
    await userEvent.click(canvas.getByRole('button', { name: 'Beitrag planen' }));
    expect(args.onPlan).toHaveBeenCalled();
  },
};

export const WeekWithoutPlanning: Story = {
  args: { view: 'week', label: 'KW 46 · 9.–15. November 2026', canPlan: false },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'true');
    expect(canvas.queryByRole('button', { name: 'Beitrag planen' })).toBeNull();
    await userEvent.click(canvas.getByRole('button', { name: 'Heute' }));
    expect(args.onToday).toHaveBeenCalled();
  },
};
