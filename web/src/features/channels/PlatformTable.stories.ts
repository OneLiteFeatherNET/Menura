import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PlatformTable from './PlatformTable.vue';
import { auditedAudit, twitch, youtube } from './channels.fixtures';
import { platformRows } from './model';

const meta = {
  title: 'Channels/PlatformTable',
  component: PlatformTable,
  args: {
    rows: platformRows([youtube, twitch]),
    canAdminister: true,
    'onOpenAudit': fn(),
  },
} satisfies Meta<typeof PlatformTable>;
export default meta;

type Story = StoryObj<typeof meta>;

/** An administrator records the audit status of an app from its row. */
export const AdminRecordsAuditStatus: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const youtubeRow = canvas.getByRole('row', { name: /YouTube/ });
    expect(within(youtubeRow).getByText('Öffentliche Sichtbarkeit nicht verfügbar')).toBeVisible();
    await userEvent.click(within(youtubeRow).getByRole('button', { name: 'Prüfstatus erfassen' }));
    expect(args.onOpenAudit).toHaveBeenCalledWith(expect.objectContaining({ platform: 'youtube' }));
  },
};

/** A viewer reads the capabilities and the audit status, and cannot record one. */
export const ViewerReadOnly: Story = {
  args: { canAdminister: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button')).toBeNull();
    expect(canvas.getByText('Nicht geprüft')).toBeVisible();
    expect(canvas.getByText('Freigabe widerrufen')).toBeVisible();
  },
};

/** The three audit states carry their text, so the status reads without colour. */
export const AuditStates: Story = {
  args: { rows: platformRows([{ ...youtube, audit: auditedAudit }, twitch]) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Geprüft')).toBeVisible();
    expect(canvas.getByText('Unbekannt')).toBeVisible();
  },
};
