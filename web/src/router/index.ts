import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type RouterHistory,
} from 'vue-router';
import AnalyticsView from '../views/AnalyticsView.vue';
import BrandKitView from '../views/BrandKitView.vue';
import JoinAttributionView from '../views/JoinAttributionView.vue';
import OverviewView from '../views/OverviewView.vue';
import PlaceholderView from '../views/PlaceholderView.vue';
import RenderBundleView from '../views/RenderBundleView.vue';
import SeriesDetailView from '../views/SeriesDetailView.vue';
import SeriesView from '../views/SeriesView.vue';

export interface NavItem {
  to: string;
  label: string;
}

export interface NavGroup {
  id: string;
  title: string;
  items: readonly NavItem[];
}

/** Working pages of Phase 1, grouped by purpose in the rail. */
export const NAV_GROUPS: readonly NavGroup[] = [
  {
    id: 'overview',
    title: 'Überblick',
    items: [{ to: '/', label: 'Übersicht' }],
  },
  {
    id: 'recognition',
    title: 'Wiedererkennung',
    items: [
      { to: '/serien', label: 'Serien' },
      { to: '/brand-kit', label: 'Brand-Kit' },
    ],
  },
  {
    id: 'measurability',
    title: 'Messbarkeit',
    items: [
      { to: '/join-attribution', label: 'Join-Attribution' },
      { to: '/analytics', label: 'Analytics' },
    ],
  },
];

/** Later phases of Menura: greyed in the rail, each opens a placeholder page. */
export const LATER_PHASES: readonly (NavItem & { phase: number; sentence: string })[] = [
  {
    to: '/spaeter/planung',
    label: 'Planung & Kalender',
    phase: 2,
    sentence: 'Hier entstehen die Veröffentlichungsplanung und der Kalender pro Kanal.',
  },
  {
    to: '/spaeter/hub-mcp',
    label: 'Hub-MCP',
    phase: 3,
    sentence: 'Hier stellt der Hub seine Daten über MCP für Werkzeuge und Agenten bereit.',
  },
  {
    to: '/spaeter/aufnahmen',
    label: 'Aufnahmen & Library',
    phase: 4,
    sentence: 'Hier landen Aufnahmen und die Clip-Bibliothek mit ihren Metadaten.',
  },
  {
    to: '/spaeter/schnitt',
    label: 'Lokaler Schnitt',
    phase: 5,
    sentence: 'Hier wird der lokale Schnitt der Shorts an das Render-Bundle angebunden.',
  },
  {
    to: '/spaeter/overlay',
    label: 'Overlay',
    phase: 6,
    sentence: 'Hier entsteht das Stream-Overlay mit Serien- und Join-Anzeige.',
  },
];

export const appRoutes: RouteRecordRaw[] = [
  { path: '/', name: 'overview', component: OverviewView },
  { path: '/serien', name: 'series', component: SeriesView },
  { path: '/serien/:id', name: 'series-detail', component: SeriesDetailView, props: true },
  { path: '/brand-kit', name: 'brand-kit', component: BrandKitView },
  { path: '/brand-kit/render-bundle', name: 'render-bundle', component: RenderBundleView },
  { path: '/join-attribution', name: 'join-attribution', component: JoinAttributionView },
  { path: '/analytics', name: 'analytics', component: AnalyticsView },
  ...LATER_PHASES.map((item) => ({
    path: item.to,
    name: `later-${item.to.slice('/spaeter/'.length)}`,
    component: PlaceholderView,
    props: { title: item.label, phase: item.phase, sentence: item.sentence },
  })),
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
  routes: RouteRecordRaw[] = appRoutes,
) {
  return createRouter({ history, routes });
}
