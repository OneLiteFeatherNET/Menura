import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import UploadStatus from './UploadStatus.vue';
import { IDLE_UPLOAD, type UploadState } from './uploadState';

const SESSION = '4f0c7a52-9d1e-4b8a-8e3f-2a6c5d7b9e10';
const EXISTING = '0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d';

const BASE: UploadState = {
  ...IDLE_UPLOAD,
  fileName: 'Boss-Kampf-Render.mp4',
  partCount: 4,
  sessionId: SESSION,
};

const meta = {
  title: 'Library/UploadStatus',
  component: UploadStatus,
  args: {
    state: BASE,
    onRetry: fn(),
    onCancel: fn(),
    onConfirmDuplicate: fn(),
    onClose: fn(),
  },
} satisfies Meta<typeof UploadStatus>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Idle: Story = {
  args: { state: { ...IDLE_UPLOAD } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button')).toBeNull();
    expect(canvas.queryByRole('progressbar')).toBeNull();
  },
};

export const Hashing: Story = {
  args: { state: { ...BASE, phase: 'hashing', hashedParts: 2 } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Prüfsumme wird berechnet: 2 von 4 Teilen.')).toBeVisible();
    expect(canvas.getByRole('progressbar', { name: 'Prüfsumme' })).toHaveAttribute('aria-valuenow', '50');
  },
};

export const UploadingWithProgress: Story = {
  args: { state: { ...BASE, phase: 'uploading', confirmedParts: [1, 2] } },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Hochladen: 2 von 4 Teilen im Speicher.')).toBeVisible();
    expect(canvas.getByRole('progressbar', { name: 'Hochladen' })).toHaveAttribute('aria-valuenow', '50');
    await userEvent.click(canvas.getByRole('button', { name: 'Abbrechen' }));
    expect(args.onCancel).toHaveBeenCalledTimes(1);
  },
};

export const FlaggedDuplicate: Story = {
  args: { state: { ...BASE, phase: 'flagged', duplicateOf: EXISTING } },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Diese Datei wurde schon hochgeladen');
    await userEvent.click(canvas.getByRole('button', { name: 'Trotzdem hochladen' }));
    expect(args.onConfirmDuplicate).toHaveBeenCalledTimes(1);
  },
};

export const FailedPartWithRetry: Story = {
  args: {
    state: {
      ...BASE,
      phase: 'failed',
      confirmedParts: [1, 2, 4],
      failedParts: [3],
      message: 'Teil 3 konnte nicht übertragen werden. Erneut versuchen setzt die Übertragung fort.',
    },
  },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Teil 3 konnte nicht übertragen werden.');
    await userEvent.click(canvas.getByRole('button', { name: 'Erneut versuchen' }));
    expect(args.onRetry).toHaveBeenCalledTimes(1);
  },
};

export const Completed: Story = {
  args: { state: { ...BASE, phase: 'completed', confirmedParts: [1, 2, 3, 4], assetId: EXISTING } },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Der Upload ist abgeschlossen. Die Datei steht jetzt im Pool.')).toBeVisible();
    await userEvent.click(canvas.getByRole('button', { name: 'Fertig' }));
    expect(args.onClose).toHaveBeenCalledTimes(1);
  },
};
