import type { Component } from 'vue';
import BrandKitPage from '../brand/BrandKitPage.vue';
import PoolPage from '../library/PoolPage.vue';
import ChannelsPage from '../features/channels/ChannelsPage.vue';
import OverviewPage from './OverviewPage.vue';

/**
 * The areas of the app, in the order the navigation shows them. This is the one place that decides what a signed-in
 * person can open: a later page is added by giving its entry a component and setting `exists` to true.
 *
 * Icons are Material Design icon paths in a 24 by 24 box; the labels are always shown next to them.
 */

/** 'any' lets every signed-in person open the area; otherwise one of the listed roles is needed (as RequiresRole). */
export type RequiredRoles = 'any' | readonly string[];

export interface AppArea {
  path: string;
  label: string;
  icon: string;
  roles: RequiredRoles;
  /** False while the page does not exist: such an area has no route and is not listed. */
  exists: boolean;
  component?: Component;
}

const ICON_HOME = 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z';
const ICON_POOL =
  'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z';
const ICON_CALENDAR =
  'M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z';
const ICON_SERIES =
  'M4 14h4v-4H4v4zm0 5h4v-4H4v4zM4 9h4V5H4v4zm5 5h12v-4H9v4zm0 5h12v-4H9v4zM9 5v4h12V5H9z';
const ICON_ANALYTICS = 'M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z';
const ICON_BRAND_KIT =
  'M12 3a9 9 0 0 0 0 18c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z';
const ICON_CHANNELS =
  'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z';

// The areas whose pages do not exist yet keep their place in the order. Their roles are set when the page arrives.
export const AREAS: readonly AppArea[] = [
  { path: '/', label: 'Übersicht', icon: ICON_HOME, roles: 'any', exists: true, component: OverviewPage },
  { path: '/pool', label: 'Pool', icon: ICON_POOL, roles: 'any', exists: true, component: PoolPage },
  { path: '/kalender', label: 'Kalender', icon: ICON_CALENDAR, roles: 'any', exists: false },
  { path: '/serien', label: 'Serien', icon: ICON_SERIES, roles: 'any', exists: false },
  { path: '/analytics', label: 'Analytics', icon: ICON_ANALYTICS, roles: 'any', exists: false },
  { path: '/brand-kit', label: 'Brand-Kit', icon: ICON_BRAND_KIT, roles: 'any', exists: true, component: BrandKitPage },
  { path: '/channels', label: 'Kanäle', icon: ICON_CHANNELS, roles: 'any', exists: true, component: ChannelsPage },
];

/** True when a person with these roles may open the area. A role grants only itself: there is no hierarchy. */
export function canOpen(area: AppArea, roles: readonly string[]): boolean {
  return area.roles === 'any' || area.roles.some((role) => roles.includes(role));
}

/** The areas a person with these roles sees in the navigation: the existing pages they may open, in order. */
export function visibleAreas(roles: readonly string[], areas: readonly AppArea[] = AREAS): AppArea[] {
  return areas.filter((area) => area.exists && canOpen(area, roles));
}

/** A path without trailing slashes; the root stays '/'. */
export function normalizePath(path: string): string {
  return path.replace(/\/+$/, '') || '/';
}

/** The area with exactly this path, whether or not its page exists yet. */
export function findArea(path: string, areas: readonly AppArea[] = AREAS): AppArea | undefined {
  const normalized = normalizePath(path);
  return areas.find((area) => area.path === normalized);
}
