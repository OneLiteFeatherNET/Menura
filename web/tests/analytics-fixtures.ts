import { vi } from 'vitest';
import type { Channel } from '../src/api/types';
import type { Metric } from '../src/features/analytics/types';

// Fixtures for the analytics tests. Each test builds its own fetch stub, so no state is shared between tests.

export const CHANNELS: Channel[] = [
  { id: 'yt-de', platform: 'youtube', name: 'OneLiteFeather (YouTube)' },
  { id: 'tt-de', platform: 'tiktok', name: 'OneLiteFeather (TikTok)' },
  { id: 'ig-de', platform: 'instagram', name: 'OneLiteFeather (Instagram)' },
];

/** The catalogue as the server serves it (see server PrototypeAnalyticsController and MetricCatalogue). */
export const METRICS: Metric[] = [
  { id: 'views', label: 'Aufrufe', platforms: ['youtube', 'tiktok', 'instagram'] },
  { id: 'engagedViews', label: 'Engaged Views', platforms: ['youtube'] },
  { id: 'avgViewPercent', label: 'Ø angesehen (%)', platforms: ['youtube'] },
  { id: 'avgWatchSeconds', label: 'Ø Wiedergabezeit (s)', platforms: ['youtube', 'instagram'] },
  { id: 'likes', label: 'Likes', platforms: ['youtube', 'tiktok', 'instagram'] },
  { id: 'comments', label: 'Kommentare', platforms: ['youtube', 'tiktok', 'instagram'] },
  { id: 'shares', label: 'Geteilt', platforms: ['youtube', 'tiktok', 'instagram'] },
  { id: 'saves', label: 'Gespeichert', platforms: ['instagram'] },
  { id: 'reach', label: 'Reichweite', platforms: ['instagram'] },
  { id: 'subscribersGained', label: 'Neue Abonnenten', platforms: ['youtube'] },
];

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Replaces the global fetch. The handler answers a request with a body, or returns undefined to fall back to the
 * default answers (channels, catalogue, empty aggregates). Unknown routes answer 404 as Problem Details.
 */
export function stubAnalyticsFetch(handle: (url: URL) => unknown = () => undefined) {
  const fake = vi.fn(async (input: string) => {
    const url = new URL(input, 'http://localhost');
    const body = handle(url) ?? defaultAnswer(url);
    if (body === undefined) {
      return json({ status: 404, title: 'Not Found' }, 404);
    }
    return json(body);
  });
  vi.stubGlobal('fetch', fake);
  return fake;
}

function defaultAnswer(url: URL): unknown {
  if (url.pathname === '/api/prototype/channels') {
    return CHANNELS;
  }
  if (url.pathname === '/api/prototype/analytics/metrics') {
    return METRICS;
  }
  if (url.pathname.endsWith('/aggregate')) {
    return { metric: url.searchParams.get('metric'), groupBy: 'day', offset: '+24h', rows: [], missing: [] };
  }
  if (url.pathname.endsWith('/heatmap')) {
    return { cells: [], missing: [] };
  }
  return undefined;
}
