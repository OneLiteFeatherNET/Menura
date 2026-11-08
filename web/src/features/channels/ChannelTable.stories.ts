import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import ChannelTable from './ChannelTable.vue';
import { connectedChannel, reconnectChannel } from './channels.fixtures';
import { channelRows } from './model';

const meta = {
  title: 'Channels/ChannelTable',
  component: ChannelTable,
  args: {
    rows: channelRows([connectedChannel, reconnectChannel]),
    canManage: true,
    busy: false,
    'onReconnect': fn(),
    'onDisconnect': fn(),
  },
} satisfies Meta<typeof ChannelTable>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Empty: Story = {
  args: { rows: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Noch kein Kanal verbunden.')).toBeVisible();
  },
};

export const Connected: Story = {
  args: { rows: channelRows([connectedChannel]) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Verbunden')).toBeVisible();
    expect(canvas.getByRole('button', { name: 'Trennen' })).toBeEnabled();
    expect(canvas.queryByRole('button', { name: 'Neu verbinden' })).toBeNull();
  },
};

/** The grant is dead: the reason is shown and the reconnect action starts the consent of the same channel. */
export const NeedsReconnection: Story = {
  args: { rows: channelRows([reconnectChannel]) },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Freigabe wurde beim Anbieter entfernt.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Neu verbinden' }));
    expect(args.onReconnect).toHaveBeenCalledWith(expect.objectContaining({ id: reconnectChannel.id }));
  },
};

/** Disconnect asks in place first; cancelling keeps the channel, confirming reports it. */
export const DisconnectNeedsConfirmation: Story = {
  args: { rows: channelRows([connectedChannel]) },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Trennen' }));
    expect(canvas.getByText(/Die gespeicherten Zugangsdaten werden gelöscht/)).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Abbrechen' }));
    expect(args.onDisconnect).not.toHaveBeenCalled();

    await userEvent.click(canvas.getByRole('button', { name: 'Trennen' }));
    await userEvent.click(canvas.getByRole('button', { name: 'Kanal trennen' }));
    expect(args.onDisconnect).toHaveBeenCalledWith(expect.objectContaining({ id: connectedChannel.id }));
  },
};

/** A viewer sees the state and the reason, and no action at all. */
export const ViewerReadOnly: Story = {
  args: { rows: channelRows([connectedChannel, reconnectChannel]), canManage: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button')).toBeNull();
    expect(canvas.queryByRole('columnheader', { name: 'Aktionen' })).toBeNull();
    expect(canvas.getByText('Neu verbinden nötig')).toBeVisible();
  },
};

export const Busy: Story = {
  args: { rows: channelRows([reconnectChannel]), busy: true },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('button', { name: 'Neu verbinden' })).toBeDisabled();
  },
};
