import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, waitFor, within } from 'storybook/test';
import ChannelsView from './ChannelsView.vue';
import { connectedChannel, reconnectChannel, twitch, youtube } from './channels.fixtures';

const admin = { canConnect: true, canAdminister: true };
const viewer = { canConnect: false, canAdminister: false };

const meta = {
  title: 'Channels/ChannelsView',
  component: ChannelsView,
  args: {
    status: 'ready',
    platforms: [youtube, twitch],
    channels: [],
    permissions: admin,
    busy: false,
    notice: null,
    actionError: null,
    connectOpen: false,
    auditTarget: null,
    'onRetry': fn(),
    'onOpenConnect': fn(),
    'onCloseConnect': fn(),
    'onConnect': fn(),
    'onReconnect': fn(),
    'onDisconnect': fn(),
    'onOpenAudit': fn(),
    'onCloseAudit': fn(),
    'onSaveAudit': fn(),
  },
} satisfies Meta<typeof ChannelsView>;
export default meta;

type Story = StoryObj<typeof meta>;

/** The first visit of an administrator: no channel yet, the platforms are listed, and the connect action is offered. */
export const EmptyAdmin: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { level: 1, name: 'Kanäle' })).toBeVisible();
    expect(canvas.getByText('Noch kein Kanal verbunden.')).toBeVisible();
    expect(canvas.getByRole('table', { name: 'Plattformen' })).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Kanal verbinden' }));
    expect(args.onOpenConnect).toHaveBeenCalledTimes(1);
  },
};

export const Connected: Story = {
  args: { channels: [connectedChannel] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Verbunden')).toBeVisible();
    expect(canvas.getByText('Morgenstream')).toBeVisible();
    expect(canvas.queryByText('Noch kein Kanal verbunden.')).toBeNull();
  },
};

export const NeedsReconnection: Story = {
  args: { channels: [reconnectChannel] },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Die Freigabe wurde beim Anbieter entfernt.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Neu verbinden' }));
    expect(args.onReconnect).toHaveBeenCalledWith(expect.objectContaining({ id: reconnectChannel.id }));
  },
};

/** The platform is down or the session cannot be read: the page says so and offers a retry, nothing else. */
export const Unavailable: Story = {
  args: { status: 'error', platforms: [], channels: [] },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Kanäle und Plattformen konnten nicht geladen werden.');
    expect(canvas.queryByRole('button', { name: 'Kanal verbinden' })).toBeNull();
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};

/** A viewer sees the channels and the platforms without a single action. */
export const ViewerReadOnly: Story = {
  args: { permissions: viewer, channels: [connectedChannel, reconnectChannel] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Neu verbinden nötig')).toBeVisible();
    expect(canvas.queryByRole('button', { name: 'Kanal verbinden' })).toBeNull();
    expect(canvas.queryByRole('button', { name: 'Trennen' })).toBeNull();
    expect(canvas.queryByRole('button', { name: 'Neu verbinden' })).toBeNull();
    expect(canvas.queryByRole('button', { name: 'Prüfstatus erfassen' })).toBeNull();
  },
};

/** The outcome of the consent comes back as a status message on this page. */
export const AfterConsent: Story = {
  args: { channels: [connectedChannel], notice: 'Der Kanal ist verbunden.' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Der Kanal ist verbunden.');
  },
};

/** The connect dialog: the platform is the only input, and the choice starts the consent. */
export const ConnectDialog: Story = {
  args: { connectOpen: true },
  play: async ({ args }) => {
    const dialog = await waitFor(() => {
      const found = document.querySelector('dialog[open]');
      expect(found).not.toBeNull();
      return found as HTMLElement;
    });
    const scoped = within(dialog);
    expect(scoped.queryByLabelText(/token|schlüssel|passwort/i)).toBeNull();
    await userEvent.click(scoped.getByRole('combobox', { name: 'Plattform' }));
    await userEvent.click(scoped.getByRole('option', { name: 'YouTube' }));
    await userEvent.click(scoped.getByRole('button', { name: 'Weiter zur Plattform' }));
    expect(args.onConnect).toHaveBeenCalledWith('youtube');
  },
};

/** Recording the audit status: the form starts from the recorded status and the source is sent with it. */
export const AuditDialog: Story = {
  args: { auditTarget: youtube },
  play: async ({ args }) => {
    const dialog = await waitFor(() => {
      const found = document.querySelector('dialog[open]');
      expect(found).not.toBeNull();
      return found as HTMLElement;
    });
    const scoped = within(dialog);
    expect(scoped.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Nicht geprüft');
    await userEvent.click(scoped.getByRole('button', { name: 'Speichern' }));
    expect(args.onSaveAudit).toHaveBeenCalledWith({ platform: 'youtube', status: 'unaudited', source: '' });
  },
};
