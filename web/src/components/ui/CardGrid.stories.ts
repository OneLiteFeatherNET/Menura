import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, within } from 'storybook/test';
import CardGrid from './CardGrid.vue';

const meta = {
  title: 'UI/CardGrid',
  component: CardGrid,
  args: { label: 'Ausschnitte' },
} satisfies Meta<typeof CardGrid>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: (args) => ({
    components: { CardGrid },
    setup: () => ({ args, cards: [1, 2, 3] }),
    template: '<CardGrid v-bind="args"><li v-for="n in cards" :key="n" class="demo-card">Karte {{ n }}</li></CardGrid>',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('list', { name: 'Ausschnitte' })).toBeVisible();
    expect(canvas.getAllByRole('listitem')).toHaveLength(3);
  },
};

export const ManyCards: Story = {
  render: (args) => ({
    components: { CardGrid },
    setup: () => ({ args, cards: Array.from({ length: 12 }, (_, i) => i + 1) }),
    template: '<CardGrid v-bind="args"><li v-for="n in cards" :key="n" class="demo-card">Karte {{ n }}</li></CardGrid>',
  }),
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getAllByRole('listitem')).toHaveLength(12);
  },
};

export const Empty: Story = {
  args: { emptyText: 'Noch keine Ausschnitte.' },
  render: (args) => ({
    components: { CardGrid },
    setup: () => ({ args }),
    template: '<CardGrid v-bind="args" />',
  }),
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Noch keine Ausschnitte.')).toBeVisible();
  },
};

export const LongCardText: Story = {
  render: (args) => ({
    components: { CardGrid },
    setup: () => ({ args }),
    template:
      '<CardGrid v-bind="args"><li class="demo-card">Ein sehr langer Kartentitel mit Wörtern, die umbrechen, und einer_extrem_langen_Kennung_ohne_Leerzeichen</li></CardGrid>',
  }),
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/sehr langer Kartentitel/)).toBeVisible();
  },
};
