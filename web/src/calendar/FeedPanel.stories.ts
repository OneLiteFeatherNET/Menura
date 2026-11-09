import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import FeedPanel from './FeedPanel.vue';

const ADDRESS = 'https://api-menura.onelitefeather.net/calendar/feed/abc123.secret';

const meta = {
  title: 'Calendar/FeedPanel',
  component: FeedPanel,
  args: { feedUrl: null, busy: false, error: undefined, onIssue: fn(), onRevoke: fn() },
} satisfies Meta<typeof FeedPanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const WithoutAddress: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('textbox')).toBeNull();
    await userEvent.click(canvas.getByRole('button', { name: 'Kalender-Adresse erstellen' }));
    expect(args.onIssue).toHaveBeenCalled();
  },
};

export const ShowsAddressOnce: Story = {
  args: { feedUrl: ADDRESS },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const address = canvas.getByRole('textbox', { name: 'Feed-Adresse' });
    expect(address).toHaveValue(ADDRESS);
    expect(address).toHaveAttribute('readonly');
    expect(canvas.getByText('Die Adresse wird nur jetzt angezeigt.')).toBeVisible();

    await userEvent.click(canvas.getByRole('button', { name: 'Neue Adresse erstellen' }));
    expect(args.onIssue).toHaveBeenCalled();
    await userEvent.click(canvas.getByRole('button', { name: 'Adresse sperren' }));
    expect(args.onRevoke).toHaveBeenCalled();
  },
};

export const Failed: Story = {
  args: { error: 'Die Kalender-Adresse konnte nicht erstellt werden.' },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('alert')).toHaveTextContent('Die Kalender-Adresse konnte nicht erstellt werden.');
  },
};
