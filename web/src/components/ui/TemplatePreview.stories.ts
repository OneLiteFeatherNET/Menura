import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import { sampleValues } from '../../brand/brandKit';
import { sampleShort } from '../../brand/sampleKit';
import TemplatePreview from './TemplatePreview.vue';

const meta = {
  title: 'UI/TemplatePreview',
  component: TemplatePreview,
  args: {
    template: sampleShort,
    values: sampleValues(sampleShort),
    label: 'Vorschau Short 9:16 mit Beispielwerten',
  },
} satisfies Meta<typeof TemplatePreview>;
export default meta;

type Story = StoryObj<typeof meta>;

/** The short 9:16 with its sample parameters: the title, the caption slot and the call to action with its join domain. */
export const ShortWithSampleParameters: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('group', { name: 'Vorschau Short 9:16 mit Beispielwerten' })).toBeVisible();
    expect(canvas.getByText('Beispieltitel')).toBeVisible();
    expect(canvas.getByText('join.example')).toBeVisible();
    expect(canvasElement.querySelectorAll('[data-slot]')).toHaveLength(sampleShort.slots.length);
  },
};

export const WithSafeZoneGuide: Story = {
  args: { showSafeZone: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByTestId('safe-zone-top')).toBeVisible();
    expect(canvas.getByTestId('safe-zone-bottom')).toBeVisible();
  },
};

/** A parameter without a sample value shows its name in brackets, so an unfilled slot is visible. */
export const MissingValueShowsTheParameter: Story = {
  args: { values: {} },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('[title]')).toBeVisible();
  },
};
