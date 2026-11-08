import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, within } from 'storybook/test';
import TranscriptUpload from './TranscriptUpload.vue';

const meta = {
  title: 'Library/TranscriptUpload',
  component: TranscriptUpload,
  args: {
    itemTitle: 'Coop-Abend',
    phase: 'idle',
    error: null,
    segmentCount: null,
    onUpload: fn(),
  },
} satisfies Meta<typeof TranscriptUpload>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Idle: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByLabelText('Transkript-Datei')).toBeVisible();
    expect(canvas.getByRole('button', { name: 'Transkript hochladen' })).toBeDisabled();
  },
};

export const ParseError: Story = {
  args: {
    error: 'Zeile 7 der Datei konnte nicht gelesen werden. Bitte die Zeitangabe oder den Text dieser Zeile prüfen.',
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Zeile 7 der Datei konnte nicht gelesen werden.');
  },
};

export const Uploaded: Story = {
  args: { phase: 'done', segmentCount: 42 },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Transkript gespeichert: 42 Abschnitte.')).toBeVisible();
  },
};
