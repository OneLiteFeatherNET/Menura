import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import SeriesForm from './SeriesForm.vue';

const meta = {
  title: 'Series/SeriesForm',
  component: SeriesForm,
  args: {
    colourTokens: ['primary', 'on-primary', 'secondary'],
    templates: ['short-9x16'],
    busy: false,
    'onSubmit': fn(),
  },
} satisfies Meta<typeof SeriesForm>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByRole('textbox', { name: 'Name' }), 'Live-Migration');
    await userEvent.type(canvas.getByRole('textbox', { name: 'Badge-Text (Deutsch)' }), 'Live');
    await userEvent.type(canvas.getByRole('textbox', { name: 'Hashtag' }), '#livemigration');
    await userEvent.click(canvas.getByRole('button', { name: 'Serie anlegen' }));
    expect(args.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Live-Migration',
        badgeText: { de: 'Live' },
        hashtag: '#livemigration',
        colourToken: 'primary',
        badgeToken: 'on-primary',
        layoutTemplate: 'short-9x16',
        audience: 'developers',
        weekday: 1,
        postingSlot: 18,
      }),
    );
  },
};

export const InvalidHashtag: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByRole('textbox', { name: 'Name' }), 'Survival');
    await userEvent.type(canvas.getByRole('textbox', { name: 'Badge-Text (Deutsch)' }), 'Survival');
    await userEvent.type(canvas.getByRole('textbox', { name: 'Hashtag' }), 'survival');
    await userEvent.click(canvas.getByRole('button', { name: 'Serie anlegen' }));
    expect(canvas.getByText(/Der Hashtag beginnt mit #/)).toBeVisible();
    expect(args.onSubmit).not.toHaveBeenCalled();
  },
};

export const RefusedByTheServer: Story = {
  args: {
    error: 'Das Badge-Textfarbe reicht nicht aus: Normaler Text braucht mindestens 4.5:1.',
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('mindestens 4.5:1');
  },
};

export const Busy: Story = {
  args: { busy: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('button', { name: 'Serie anlegen' })).toBeDisabled();
  },
};
