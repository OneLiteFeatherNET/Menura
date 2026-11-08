import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import NavRail, { type NavRailItem } from './NavRail.vue';

// The items a role may open are chosen by the app shell; the rail only shows what it receives. These stories use the
// areas of the app as the shell passes them for a viewer and for an admin.
const ICON = 'M4 4h16v16H4z';
const OVERVIEW: NavRailItem = { path: '/', label: 'Übersicht', icon: ICON };
const POOL: NavRailItem = { path: '/pool', label: 'Pool', icon: ICON };
const BRAND_KIT: NavRailItem = { path: '/brand-kit', label: 'Brand-Kit', icon: ICON };
const CHANNELS: NavRailItem = { path: '/channels', label: 'Kanäle', icon: ICON };
const ALL_AREAS = [OVERVIEW, POOL, BRAND_KIT, CHANNELS];

const meta = {
  title: 'UI/NavRail',
  component: NavRail,
  args: {
    items: ALL_AREAS,
    activePath: '/',
    collapsed: false,
    onNavigate: fn(),
  },
} satisfies Meta<typeof NavRail>;
export default meta;

type Story = StoryObj<typeof meta>;

/** The links of the rail, in the order they are shown. */
function railLinks(canvasElement: HTMLElement): HTMLAnchorElement[] {
  return [...canvasElement.querySelectorAll<HTMLAnchorElement>('nav a')];
}

export const Viewer: Story = {
  args: { items: [OVERVIEW, POOL, BRAND_KIT, CHANNELS] },
  play: async ({ canvasElement }) => {
    const names = railLinks(canvasElement).map((link) => link.getAttribute('aria-label'));
    expect(names, 'the items of the viewer in order').toEqual(['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle']);
  },
};

export const FewerAreas: Story = {
  args: { items: [OVERVIEW, POOL] },
  play: async ({ canvasElement }) => {
    expect(railLinks(canvasElement)).toHaveLength(2);
  },
};

export const ActiveItem: Story = {
  args: { activePath: '/pool' },
  play: async ({ canvasElement }) => {
    const pool = within(canvasElement).getByRole('link', { name: 'Pool' });
    expect(pool).toHaveAttribute('aria-current', 'page');
    expect(within(canvasElement).getByRole('link', { name: 'Kanäle' })).not.toHaveAttribute('aria-current');
  },
};

export const Collapsed: Story = {
  args: { collapsed: true },
  play: async ({ canvasElement }) => {
    const label = canvasElement.querySelector<HTMLElement>('nav .nav-rail__label');
    expect(label, 'the label of an item').not.toBeNull();
    expect(label, 'the label is hidden in the collapsed rail').not.toBeVisible();
    expect(within(canvasElement).getByRole('link', { name: 'Pool' })).toBeVisible();
  },
};

export const Empty: Story = {
  args: { items: [] },
  play: async ({ canvasElement }) => {
    expect(within(canvasElement).getByRole('navigation', { name: 'Hauptnavigation' })).toBeVisible();
    expect(railLinks(canvasElement)).toHaveLength(0);
  },
};

export const SelectsAnItem: Story = {
  play: async ({ canvasElement, args }) => {
    await userEvent.click(within(canvasElement).getByRole('link', { name: 'Brand-Kit' }));
    expect(args.onNavigate).toHaveBeenCalledWith('/brand-kit');
  },
};
