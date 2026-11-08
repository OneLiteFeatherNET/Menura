import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import AppTextField from './AppTextField.vue';

const meta = {
  title: 'UI/AppTextField',
  component: AppTextField,
  args: {
    label: 'Titel',
    modelValue: '',
    'onUpdate:modelValue': fn(),
  },
} satisfies Meta<typeof AppTextField>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByRole('textbox', { name: 'Titel' }), 'a');
    expect(args['onUpdate:modelValue']).toHaveBeenCalledWith('a');
  },
};

export const WithValue: Story = {
  args: { modelValue: 'Morgenstream' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('textbox', { name: 'Titel' })).toHaveValue('Morgenstream');
  },
};

export const Empty: Story = {
  args: { modelValue: '', placeholder: 'Titel eingeben' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('textbox', { name: 'Titel' })).toHaveAttribute('placeholder', 'Titel eingeben');
  },
};

export const WithError: Story = {
  args: { modelValue: '', error: 'Der Titel ist erforderlich.' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const field = canvas.getByRole('textbox', { name: 'Titel' });
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(canvas.getByText('Der Titel ist erforderlich.')).toBeVisible();
  },
};

export const Disabled: Story = {
  args: { modelValue: 'Gesperrt', disabled: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('textbox', { name: 'Titel' })).toBeDisabled();
  },
};

export const LongTextWithLimit: Story = {
  args: {
    modelValue: 'Ein sehr langer Titel für einen Ausschnitt, der im Feld nicht abgeschnitten werden darf',
    maxlength: 120,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('textbox', { name: 'Titel' })).toHaveAttribute('maxlength', '120');
    expect(canvas.getByText('87/120')).toBeVisible();
  },
};
