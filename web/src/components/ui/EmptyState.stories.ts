import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import EmptyState from './EmptyState.vue';

const meta = {
  title: 'UI/EmptyState',
  component: EmptyState,
  args: {
    title: 'Noch keine Einträge',
    description: 'Sobald Aufnahmen in den Pool kommen, erscheinen sie hier.',
  },
} satisfies Meta<typeof EmptyState>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { name: 'Noch keine Einträge' })).toBeVisible();
    expect(canvas.getByText(/erscheinen sie hier/)).toBeVisible();
  },
};

export const WithAction: Story = {
  render: (args) => ({
    components: { EmptyState },
    setup: () => ({ args, onAdd: fn() }),
    template: '<EmptyState v-bind="args"><button type="button" @click="onAdd">Aufnahme hinzufügen</button></EmptyState>',
  }),
  play: async ({ canvasElement }) => {
    await userEvent.click(within(canvasElement).getByRole('button', { name: 'Aufnahme hinzufügen' }));
  },
};

export const LongText: Story = {
  args: {
    title: 'Ein sehr langer Hinweistitel, der über mehrere Zeilen umbrechen darf und trotzdem vollständig lesbar bleibt',
    description:
      'Diese Beschreibung erklärt ausführlich, warum der Bereich leer ist und welche Schritte als Nächstes sinnvoll sind, damit niemand im Ungewissen bleibt.',
  },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/Nächstes sinnvoll sind/)).toBeVisible();
  },
};
