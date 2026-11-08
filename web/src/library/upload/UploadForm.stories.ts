import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import UploadForm from './UploadForm.vue';

const EMPTY_VALUES = { durationSeconds: '', width: '', height: '', streamTitle: '', streamDate: '' };

const meta = {
  title: 'Library/UploadForm',
  component: UploadForm,
  args: {
    mode: 'finished',
    fileName: '',
    values: EMPTY_VALUES,
    errors: {},
    'onUpdate:mode': fn(),
    'onUpdate:values': fn(),
    onPick: fn(),
  },
} satisfies Meta<typeof UploadForm>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Idle: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByLabelText('Datei')).toBeVisible();
    expect(canvas.queryByLabelText('Titel des Streams')).toBeNull();
  },
};

export const RawMaterialWithErrors: Story = {
  args: {
    mode: 'raw',
    fileName: 'stream-2026-11-01.mkv',
    values: { durationSeconds: '3600', width: '1920', height: '1080', streamTitle: '', streamDate: '2026-02-30' },
    errors: {
      streamTitle: 'Bitte den Titel des Streams angeben.',
      streamDate: 'Bitte das Datum des Streams angeben.',
    },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByLabelText('Titel des Streams')).toBeVisible();
    expect(canvas.getByText('Bitte den Titel des Streams angeben.')).toBeVisible();
    expect(canvas.getByText('Bitte das Datum des Streams angeben.')).toBeVisible();
  },
};

export const EditsDuration: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByLabelText('Länge in Sekunden'), '9');
    expect(args['onUpdate:values']).toHaveBeenCalled();
  },
};
