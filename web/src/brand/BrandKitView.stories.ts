import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import BrandKitView from './BrandKitView.vue';
import { sampleVersion } from './sampleKit';

const meta = {
  title: 'Brand/BrandKitView',
  component: BrandKitView,
  args: { status: 'ready', version: sampleVersion },
} satisfies Meta<typeof BrandKitView>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Published: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { name: 'Markenkit' })).toBeVisible();
    expect(canvas.getByText('Version 1')).toBeVisible();
    expect(canvas.getByRole('list', { name: 'Farbtokens' })).toBeVisible();
    expect(canvas.getByText('Hell #6638e4')).toBeVisible();
    expect(canvas.getByRole('group', { name: /Vorschau Short 9:16/ })).toBeVisible();
  },
};

export const Loading: Story = {
  args: { status: 'loading', version: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('status')).toHaveTextContent('Lädt');
  },
};

export const ReadFailedWithRetry: Story = {
  args: { status: 'error', version: null },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Das Markenkit konnte nicht geladen werden.');
    expect(canvas.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible();
  },
};

export const NoKitPublishedYet: Story = {
  args: { status: 'ready', version: null },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Noch kein Markenkit veröffentlicht.')).toBeVisible();
  },
};
