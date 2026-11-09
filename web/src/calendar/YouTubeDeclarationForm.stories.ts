import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import YouTubeDeclarationForm from './YouTubeDeclarationForm.vue';

const meta = {
  title: 'Calendar/YouTubeDeclarationForm',
  component: YouTubeDeclarationForm,
  args: {
    visibility: null,
    madeForKids: null,
    plannedShort: false,
    busy: false,
    error: undefined,
    onSave: fn(),
  },
} satisfies Meta<typeof YouTubeDeclarationForm>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Empty: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Sichtbarkeit' })).toHaveTextContent('Sichtbarkeit wählen');
    expect(canvas.getByRole('radio', { name: /Ja, das Video ist für Kinder bestimmt/ })).not.toBeChecked();

    // Nothing is sent before the two answers are given.
    await userEvent.click(canvas.getByRole('button', { name: 'Angaben speichern' }));
    expect(canvas.getByRole('alert')).toHaveTextContent('Wählen Sie die Sichtbarkeit.');
    expect(args.onSave).not.toHaveBeenCalled();
  },
};

export const AnswersGiven: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Sichtbarkeit' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Nicht gelistet' }));
    await userEvent.click(canvas.getByRole('radio', { name: /Nein, das Video ist nicht für Kinder bestimmt/ }));
    await userEvent.click(canvas.getByRole('checkbox', { name: /Als Short planen/ }));

    await userEvent.click(canvas.getByRole('button', { name: 'Angaben speichern' }));

    expect(args.onSave).toHaveBeenCalledWith({ visibility: 'unlisted', madeForKids: false, planShort: true });
  },
};

export const MadeForKidsMissing: Story = {
  args: { visibility: 'private', madeForKids: null },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Angaben speichern' }));
    expect(canvas.getByRole('alert')).toHaveTextContent('Beantworten Sie, ob das Video für Kinder bestimmt ist.');
    expect(args.onSave).not.toHaveBeenCalled();
  },
};

export const Declared: Story = {
  args: { visibility: 'public', madeForKids: true, plannedShort: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Sichtbarkeit' })).toHaveTextContent('Öffentlich');
    expect(canvas.getByRole('radio', { name: /Ja, das Video ist für Kinder bestimmt/ })).toBeChecked();
    expect(canvas.getByRole('checkbox', { name: /Als Short planen/ })).toBeChecked();
  },
};

export const Saving: Story = {
  args: { busy: true, visibility: 'private', madeForKids: false },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('button', { name: 'Angaben speichern' })).toBeDisabled();
  },
};

export const ServerRefusal: Story = {
  args: { error: 'Die Angaben konnten nicht gespeichert werden.', visibility: 'private', madeForKids: false },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('alert')).toHaveTextContent('Die Angaben konnten nicht gespeichert werden.');
  },
};
