import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PlanPostDialog from './PlanPostDialog.vue';
import { ASSET_ID, CHANNEL_ID, SERIES_ID } from './samples';

// Date and time inputs take their value directly; the story sets it and reports it as the browser does.
function setInput(input: HTMLElement, value: string) {
  (input as HTMLInputElement).value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

const assets = [{ value: ASSET_ID, label: 'Video · mp4 · e1f2a3b4' }];
const channels = [{ value: CHANNEL_ID, label: 'OneLiteFeather' }];
const series = [{ value: SERIES_ID, label: 'Kubernetes in 30 s' }];

const meta = {
  title: 'Calendar/PlanPostDialog',
  component: PlanPostDialog,
  args: {
    open: true,
    assets,
    channels,
    series,
    timeZone: 'Europe/Berlin',
    busy: false,
    error: undefined,
    onClose: fn(),
    onSubmit: fn(),
  },
} satisfies Meta<typeof PlanPostDialog>;
export default meta;

type Story = StoryObj<typeof meta>;

export const PlanOnOneChannel: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('checkbox', { name: 'OneLiteFeather' }));
    await userEvent.type(canvas.getByRole('textbox', { name: 'Titel' }), 'Kubernetes in 30 Sekunden');
    setInput(canvas.getByLabelText('Datum (Installationszeitzone)'), '2026-11-09');
    setInput(canvas.getByLabelText('Uhrzeit'), '00:30');

    await userEvent.click(canvas.getByRole('button', { name: 'Entwurf anlegen' }));

    // 00:30 on Monday in Europe/Berlin is 23:30 UTC on the Sunday before.
    expect(args.onSubmit).toHaveBeenCalledWith({
      kind: 'posts',
      request: {
        assetId: ASSET_ID,
        channels: [
          { channelId: CHANNEL_ID, title: 'Kubernetes in 30 Sekunden', scheduledAt: '2026-11-08T23:30:00.000Z' },
        ],
      },
    });
  },
};

export const RefusesWithoutChannel: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Entwurf anlegen' }));
    expect(canvas.getByRole('alert')).toHaveTextContent('Wählen Sie mindestens einen Kanal aus.');
    expect(args.onSubmit).not.toHaveBeenCalled();
  },
};

export const PlanNextEpisodes: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('radio', { name: 'Nächste Folgen einer Serie' }));
    await userEvent.click(canvas.getByRole('combobox', { name: 'Serie' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Kubernetes in 30 s' }));
    await userEvent.click(canvas.getByRole('combobox', { name: 'Kanal' }));
    await userEvent.click(canvas.getByRole('option', { name: 'OneLiteFeather' }));
    setInput(canvas.getByLabelText('Anzahl Folgen (1 bis 10)'), '3');

    await userEvent.click(canvas.getByRole('button', { name: 'Folgen planen' }));

    expect(args.onSubmit).toHaveBeenCalledWith({
      kind: 'episodes',
      seriesId: SERIES_ID,
      request: { channelId: CHANNEL_ID, assetId: ASSET_ID, count: 3 },
    });
  },
};

export const NoChannelConnected: Story = {
  args: { channels: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Kein Kanal ist verbunden. Verbinden Sie zuerst einen Kanal.')).toBeVisible();
  },
};

export const ServerRefusal: Story = {
  args: { error: 'Die Planung wurde abgelehnt. Prüfen Sie die Angaben.' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('alert')).toHaveTextContent('Die Planung wurde abgelehnt.');
  },
};
