import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import { sampleEmptyOverview, sampleOverview } from '../../analytics/samples';
import AnalyticsOverviewPanel from './AnalyticsOverviewPanel.vue';

const meta = {
  title: 'Analytics/OverviewPanel',
  component: AnalyticsOverviewPanel,
  args: {
    status: 'ready',
    overview: sampleOverview(),
    days: 90,
    rangeLabel: '11.08.2026 bis 08.11.2026',
    'onRetry': fn(),
    'onChange-period': fn(),
  },
} satisfies Meta<typeof AnalyticsOverviewPanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 2, name: 'Überblick' })).toBeVisible();
    expect(canvas.getByRole('table', { name: 'Gesamtwerte des Zeitraums' })).toBeVisible();
    expect(canvas.getByRole('table', { name: 'Erfassung je Kanal' })).toBeVisible();
    expect(canvas.getByText('Erfasst')).toBeVisible();
    expect(canvas.getByText('Nicht unterstützt')).toBeVisible();
    expect(canvas.getByRole('combobox', { name: 'Zeitraum' })).toHaveTextContent('Letzte 90 Tage');
  },
};

export const ChangePeriod: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Zeitraum' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Letzte 7 Tage' }));
    expect(args['onChange-period']).toHaveBeenCalledWith(7);
  },
};

export const NoPosts: Story = {
  args: { overview: sampleEmptyOverview() },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Noch keine Kennzahlen')).toBeVisible();
    expect(canvas.getByText(/erscheinen nach der ersten Erfassung/)).toBeVisible();
    expect(canvas.getByText('Noch kein Kanal verbunden.')).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading', overview: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt …');
  },
};

export const Failed: Story = {
  args: { status: 'error', overview: null, errorMessage: 'Die Analytics konnten nicht geladen werden.' },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Die Analytics konnten nicht geladen werden.');
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};
