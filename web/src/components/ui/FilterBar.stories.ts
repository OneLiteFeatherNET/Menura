import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import { defineComponent, h, ref } from 'vue';
import AppSelect from './AppSelect.vue';
import AppTextField from './AppTextField.vue';
import FilterBar from './FilterBar.vue';

const meta = {
  title: 'UI/FilterBar',
  component: FilterBar,
  args: {
    label: 'Pool filtern',
    resettable: false,
    onReset: fn(),
  },
} satisfies Meta<typeof FilterBar>;
export default meta;

type Story = StoryObj<typeof meta>;

/** A filter bar with a search field and a select, both kept in the story's state. */
const controls = () =>
  defineComponent({
    setup() {
      const search = ref('');
      const format = ref<string | null>(null);
      return () => [
        h(AppTextField, {
          label: 'Suche',
          modelValue: search.value,
          'onUpdate:modelValue': (v: string) => (search.value = v),
        }),
        h(AppSelect, {
          label: 'Format',
          options: [
            { value: 'short', label: 'Kurz' },
            { value: 'long', label: 'Lang' },
          ],
          modelValue: format.value,
          'onUpdate:modelValue': (v: string) => (format.value = v),
        }),
      ];
    },
  });

export const Default: Story = {
  render: (args) => ({
    components: { FilterBar, Controls: controls() },
    setup: () => ({ args }),
    template: '<FilterBar v-bind="args"><Controls /></FilterBar>',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('search', { name: 'Pool filtern' })).toBeVisible();
    expect(canvas.queryByRole('button', { name: 'Filter zurücksetzen' })).toBeNull();
  },
};

export const Resettable: Story = {
  args: { resettable: true },
  render: (args) => ({
    components: { FilterBar, Controls: controls() },
    setup: () => ({ args }),
    template: '<FilterBar v-bind="args"><Controls /></FilterBar>',
  }),
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Filter zurücksetzen' }));
    expect(args.onReset).toHaveBeenCalledTimes(1);
  },
};

export const WithoutControls: Story = {
  args: { resettable: true },
  render: (args) => ({
    components: { FilterBar },
    setup: () => ({ args }),
    template: '<FilterBar v-bind="args" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('button', { name: 'Filter zurücksetzen' })).toBeVisible();
  },
};
