import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, userEvent, within } from 'storybook/test';
import { defineComponent, h, ref } from 'vue';
import AppSelect from './AppSelect.vue';

const options = [
  { value: 'short', label: 'Kurz' },
  { value: 'long', label: 'Lang' },
  { value: 'live', label: 'Live-Mitschnitt' },
];

const meta = {
  title: 'UI/AppSelect',
  component: AppSelect,
  args: {
    label: 'Format',
    options,
    modelValue: null,
  },
} satisfies Meta<typeof AppSelect>;
export default meta;

type Story = StoryObj<typeof meta>;

/** The select with its value kept in a parent, as a feature view uses it. */
const withState = (initial: string | null, extra?: () => ReturnType<typeof h>[]) =>
  defineComponent({
    setup() {
      const value = ref<string | null>(initial);
      return () =>
        h('div', { style: 'display: grid; gap: 16px; max-width: 320px' }, [
          h(AppSelect, {
            label: 'Format',
            options,
            modelValue: value.value,
            'onUpdate:modelValue': (next: string) => {
              value.value = next;
            },
          }),
          h('output', { 'data-testid': 'value' }, `Ausgewählt: ${value.value ?? 'keine'}`),
          ...(extra ? extra() : []),
        ]);
    },
  });

export const Default: Story = {
  render: () => withState(null),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Format' }));
    for (const label of ['Kurz', 'Lang', 'Live-Mitschnitt']) {
      const option = canvas.getByRole('option', { name: label });
      expect(option.textContent?.trim(), `option ${label} shows its label`).toBe(label);
      expect(option.getBoundingClientRect().height, `option ${label} has a visible box`).toBeGreaterThan(0);
    }
  },
};

export const Selected: Story = {
  render: () => withState('long'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Format' }).textContent).toContain('Lang');
  },
};

export const SelectByKeyboard: Story = {
  render: () => withState(null),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const combobox = canvas.getByRole('combobox', { name: 'Format' });
    combobox.focus();
    await userEvent.keyboard('{ArrowDown}');
    await userEvent.keyboard('{ArrowDown}');
    await userEvent.keyboard('{Enter}');
    expect(canvas.getByTestId('value').textContent).toBe('Ausgewählt: long');
    expect(canvas.queryByRole('listbox')).toBeNull();
  },
};

/**
 * The menu opens in the layout and pushes the controls below it down, so it never covers them. The text field is
 * placed directly below the select; after the menu opens, the two boxes must not overlap and the field must be the
 * element at its own centre (not the menu).
 */
export const MenuDoesNotCoverFollowingControl: Story = {
  render: () =>
    withState(null, () => [
      h('label', { for: 'after-select' }, 'Titel danach'),
      h('input', { id: 'after-select', 'data-testid': 'after', type: 'text' }),
    ]),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Format' }));
    const listbox = canvas.getByRole('listbox');
    const field = canvas.getByTestId('after');
    const menu = listbox.getBoundingClientRect();
    const input = field.getBoundingClientRect();
    const overlaps = menu.left < input.right && input.left < menu.right && menu.top < input.bottom && input.top < menu.bottom;
    expect(overlaps, 'the open menu must not overlap the text field below it').toBe(false);
    const centre = field.getBoundingClientRect();
    const hit = document.elementFromPoint(centre.left + centre.width / 2, centre.top + centre.height / 2);
    expect(hit, 'the text field must be the element at its centre').toBe(field);
    await userEvent.click(field);
    expect(document.activeElement).toBe(field);
  },
};

export const Disabled: Story = {
  args: { disabled: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('combobox', { name: 'Format' })).toBeDisabled();
  },
};

export const WithError: Story = {
  args: { error: 'Bitte ein Format wählen.' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const combobox = canvas.getByRole('combobox', { name: 'Format' });
    expect(combobox).toHaveAttribute('aria-invalid', 'true');
    expect(canvas.getByText('Bitte ein Format wählen.')).toBeVisible();
  },
};

export const NoOptions: Story = {
  args: { options: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox', { name: 'Format' }));
    expect(canvas.getByText('Keine Auswahl verfügbar.')).toBeVisible();
  },
};

export const LongLabels: Story = {
  args: {
    label: 'Ein sehr langer Feldname, der über mehrere Zeilen umbricht und trotzdem lesbar bleibt',
    options: [
      { value: 'a', label: 'Eine Option mit einem sehr langen Text, der innerhalb des Menüs umbricht' },
      { value: 'b', label: 'Kurz' },
    ],
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('combobox'));
    expect(canvas.getByRole('option', { name: /sehr langen Text/ }).textContent).toContain('umbricht');
  },
};
