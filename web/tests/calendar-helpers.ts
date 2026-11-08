import type { Channel, Series } from '../src/api/types';
import type { Capability, Publication, PublicationStatus, Step } from '../src/features/calendar/types';

/** The seed "now" of the prototype: Sunday 8 Nov 2026, 13:00 in Berlin. */
export const NOW = new Date('2026-11-08T12:00:00Z');

export const CHANNELS: Channel[] = [
  { id: 'yt-de', platform: 'youtube', name: 'OneLiteFeather (YouTube)' },
  { id: 'tt-de', platform: 'tiktok', name: 'OneLiteFeather (TikTok)' },
  { id: 'ig-de', platform: 'instagram', name: 'OneLiteFeather (Instagram)' },
] as Channel[];

export const SERIES: Series[] = [
  {
    id: 'k8s-30s',
    name: 'Kubernetes in 30 s',
    badge: { de: 'Kubernetes in 30 s', en: 'Kubernetes in 30 s' },
    colorToken: 'series-k8s',
    color: '#2F6FEB',
    badgeTextColor: '#FFFFFF',
    openingFormula: 'Kurz erklärt:',
    hashtag: '#k8sin30',
    audience: 'developers',
    weekday: 'TUESDAY',
    slot: '18:00',
    playlists: { 'yt-de': 'https://www.youtube.com/playlist?list=DEMO-K8S' },
    episodes: 2,
    active: true,
  } as Series,
];

export const PLATFORMS: Record<string, Capability> = {
  youtube: {
    nativeSchedule: true,
    note: 'Upload privat mit publishAt; YouTube schaltet zur Zeit öffentlich.',
  },
  tiktok: {
    nativeSchedule: false,
    note: 'Menura veröffentlicht zur Zeit selbst. Ohne API-Audit nur privat.',
  },
  instagram: {
    nativeSchedule: false,
    note: 'Menura veröffentlicht zur Zeit selbst. Höchstens 50 API-Posts pro 24 h.',
  },
};

const WAITING: Step[] = (['upload', 'schedule', 'metadata', 'publish', 'metrics'] as const).map((name) => ({
  name,
  state: 'waiting',
  at: null,
  detail: null,
}));

const DONE: Step[] = WAITING.map((step) => ({ ...step, state: 'done' as const, at: '2026-11-01T17:00:00Z' }));

/** A publication for a test. Defaults to a planned YouTube upload of a k8s clip at 18:00 Berlin on 10 Nov. */
export function publication(overrides: Partial<Publication> = {}): Publication {
  return {
    id: 'pub-1',
    assetId: 'a-104',
    channelId: 'yt-de',
    platform: 'youtube',
    seriesId: 'k8s-30s',
    publishAt: '2026-11-10T17:00:00Z',
    mode: 'native',
    status: 'planned' satisfies PublicationStatus,
    metadata: {
      title: 'Cilium in 30 s: Policy-Check',
      description: 'Kurz erklärt: Cilium in 30 s: Policy-Check · Spiel mit uns: onelitefeather.net',
      tags: ['#k8sin30', '#cilium'],
      playlist: 'https://www.youtube.com/playlist?list=DEMO-K8S',
    },
    steps: WAITING,
    postId: null,
    snapshots: [],
    ...overrides,
  };
}

/** A published v1 post: every step done and a post id. */
export function publishedPost(overrides: Partial<Publication> = {}): Publication {
  return publication({
    id: 'pub-p-41',
    assetId: null,
    status: 'published',
    postId: 'p-41',
    steps: DONE,
    ...overrides,
  });
}

export function jsonResponse(status: number, body: unknown): Response {
  if (status === 204 || body === undefined) {
    return new Response(null, { status });
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** A fetch double that answers by method and path (query ignored); unknown routes answer 404. */
export function fetchRoutes(routes: Record<string, () => Response>) {
  return async (input: string, init?: RequestInit): Promise<Response> => {
    const method = init?.method ?? 'GET';
    const path = input.split('?')[0] ?? input;
    const handler = routes[`${method} ${path}`];
    if (handler) {
      return handler();
    }
    return jsonResponse(404, { status: 404, title: 'Not Found', detail: 'unbekannte Route' });
  };
}

/** The routes every calendar page loads at start. */
export function baseRoutes(publications: Publication[], extra: Record<string, () => Response> = {}) {
  return {
    'GET /api/prototype/publications': () => jsonResponse(200, publications),
    'GET /api/prototype/channels': () => jsonResponse(200, CHANNELS),
    'GET /api/prototype/series': () => jsonResponse(200, SERIES),
    'GET /api/prototype/platforms': () => jsonResponse(200, PLATFORMS),
    'GET /api/prototype/assets': () =>
      jsonResponse(200, [
        {
          id: 'a-104',
          title: 'Cilium in 30 s: Policy-Check',
          seriesId: 'k8s-30s',
          tags: ['cilium', 'kubernetes'],
          stage: 'rendered',
          createdAt: '2026-11-05T21:00:00Z',
        },
      ]),
    ...extra,
  };
}
