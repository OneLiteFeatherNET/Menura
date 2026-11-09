import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import CalendarFilters from './CalendarFilters.vue';
import { EMPTY_CALENDAR_FILTERS } from './model';
import { CHANNEL_ID, SECOND_CHANNEL_ID, SERIES_ID } from './samples';

const meta = {
  title: 'Calendar/CalendarFilters',
  component: CalendarFilters,
  args: {
    modelValue: EMPTY_CALENDAR_FILTERS,
    channels: [
      { value: CHANNEL_ID, label: 'OneLiteFeather (youtube)' },
      { value: SECOND_CHANNEL_ID, label: 'Zweiter Kanal (tiktok)' },
    ],
    series: [{ value: SERIES_ID, label: 'Kubernetes in 30 s' }],
    'onUpdate:modelValue': fn(),
  },
} satisfies Meta<typeof CalendarFilters>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Kanal' })).toHaveTextContent('Alle Kanäle');
    expect(canvas.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Alle Status');

    await userEvent.click(canvas.getByRole('combobox', { name: 'Kanal' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Zweiter Kanal (tiktok)' }));
    expect(args['onUpdate:modelValue']).toHaveBeenCalledWith({
      ...EMPTY_CALENDAR_FILTERS,
      channelId: SECOND_CHANNEL_ID,
    });
  },
};

export const SeriesAndStatusChosen: Story = {
  args: {
    modelValue: { channelId: '', seriesId: SERIES_ID, status: 'approved' },
  },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Serie' })).toHaveTextContent('Kubernetes in 30 s');
    expect(canvas.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Freigegeben');

    await userEvent.click(canvas.getByRole('combobox', { name: 'Status' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Veröffentlicht' }));
    expect(args['onUpdate:modelValue']).toHaveBeenCalledWith({
      channelId: '',
      seriesId: SERIES_ID,
      status: 'published',
    });
  },
};
