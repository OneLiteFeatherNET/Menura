import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import DataTable from './DataTable.vue';

const columns = [
  { key: 'title', label: 'Titel' },
  { key: 'status', label: 'Status' },
  { key: 'duration', label: 'Dauer' },
];

const rows = [
  { id: 'a1', title: 'Morgenstream Highlights', status: 'Entwurf', duration: '00:42' },
  { id: 'b2', title: 'Speedrun-Versuch 3', status: 'Geplant', duration: '03:10' },
];

const meta = {
  title: 'UI/DataTable',
  component: DataTable,
  args: {
    caption: 'Pool-Einträge',
    columns,
    rows,
    rowKey: 'id',
    selectable: false,
    'onSelect': fn(),
  },
} satisfies Meta<typeof DataTable>;
export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('table', { name: 'Pool-Einträge' })).toBeVisible();
    expect(canvas.getAllByRole('row')).toHaveLength(3);
  },
};

/** Viewer: the title is plain text; no row can be opened for editing. */
export const ViewerReadOnly: Story = {
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).queryByRole('button')).toBeNull();
  },
};

/** Editor: the title of each row opens the entry. */
export const EditorSelectable: Story = {
  args: { selectable: true },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Speedrun-Versuch 3' }));
    expect(args.onSelect).toHaveBeenCalledWith(rows[1]);
  },
};

export const Empty: Story = {
  args: { rows: [], emptyText: 'Keine Einträge im Pool.' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText('Keine Einträge im Pool.')).toBeVisible();
  },
};

export const LongText: Story = {
  args: {
    rows: [
      {
        id: 'c3',
        title: 'Ein sehr langer Titel, der in der Tabelle umbricht und trotzdem vollständig lesbar bleibt',
        status: 'Veröffentlicht',
        duration: '12:00',
      },
    ],
  },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByText(/sehr langer Titel/)).toBeVisible();
  },
};
