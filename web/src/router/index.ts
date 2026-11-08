import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type RouterHistory,
} from 'vue-router';
import AnalyticsLayout from '../features/analytics/AnalyticsLayout.vue';
import ChannelsTab from '../features/analytics/ChannelsTab.vue';
import JoinAttributionTab from '../features/analytics/JoinAttributionTab.vue';
import MetricsTab from '../features/analytics/MetricsTab.vue';
import OverviewTab from '../features/analytics/OverviewTab.vue';
import SeriesTab from '../features/analytics/SeriesTab.vue';
import TimeTab from '../features/analytics/TimeTab.vue';
import AssetDetailView from '../features/pool/AssetDetailView.vue';
import PoolView from '../features/pool/PoolView.vue';
import CalendarView from '../features/calendar/CalendarView.vue';
import BrandKitView from '../views/BrandKitView.vue';
import OverviewView from '../views/OverviewView.vue';
import RenderBundleView from '../views/RenderBundleView.vue';
import SeriesDetailView from '../views/SeriesDetailView.vue';
import SeriesView from '../views/SeriesView.vue';

export interface NavItem {
  to: string;
  label: string;
}

/** The navigation rail, top to bottom. Prefix match marks a page active, except the overview (exact match). */
export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Übersicht' },
  { to: '/pool', label: 'Pool' },
  { to: '/kalender', label: 'Kalender' },
  { to: '/serien', label: 'Serien' },
  { to: '/analytics', label: 'Analytics' },
  { to: '/brand-kit', label: 'Brand-Kit' },
];

export const appRoutes: RouteRecordRaw[] = [
  { path: '/', name: 'overview', component: OverviewView },
  { path: '/pool', name: 'pool', component: PoolView },
  { path: '/pool/:id', name: 'pool-asset', component: AssetDetailView, props: true },
  { path: '/kalender', name: 'calendar', component: CalendarView },
  { path: '/serien', name: 'series', component: SeriesView },
  { path: '/serien/:id', name: 'series-detail', component: SeriesDetailView, props: true },
  {
    path: '/analytics',
    name: 'analytics',
    component: AnalyticsLayout,
    children: [
      { path: '', name: 'analytics-overview', component: OverviewTab },
      { path: 'kanaele', name: 'analytics-channels', component: ChannelsTab },
      { path: 'uhrzeit', name: 'analytics-time', component: TimeTab },
      { path: 'serien', name: 'analytics-series', component: SeriesTab },
      { path: 'join-attribution', name: 'analytics-join-attribution', component: JoinAttributionTab },
      { path: 'datenpunkte', name: 'analytics-metrics', component: MetricsTab },
    ],
  },
  { path: '/join-attribution', redirect: '/analytics/join-attribution' },
  { path: '/brand-kit', name: 'brand-kit', component: BrandKitView },
  { path: '/brand-kit/render-bundle', name: 'render-bundle', component: RenderBundleView },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
  routes: RouteRecordRaw[] = appRoutes,
) {
  return createRouter({ history, routes });
}
