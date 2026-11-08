// Sample series data for the stories and the tests. It is data only: no story is registered from this file.
import type { Series, SeriesDetail, SeriesOverview } from '../../api/series';

export const CHANNEL_ID = 'c0ffee00-0000-4000-8000-000000000001';

export function sampleSeries(overrides: Partial<Series> = {}): Series {
  return {
    id: 'a1b2c3d4-0000-4000-8000-000000000001',
    name: 'Kubernetes in 30 s',
    badgeText: { de: 'Kurz erklärt' },
    colourToken: 'primary',
    badgeToken: 'on-primary',
    openingFormula: 'Kurz erklärt:',
    layoutTemplate: 'short-9x16',
    hashtag: '#kubernetes30s',
    audience: 'developers',
    weekday: 1,
    postingSlot: 18,
    active: true,
    playlists: [],
    createdAt: '2026-11-02T10:00:00Z',
    updatedAt: '2026-11-02T10:00:00Z',
    ...overrides,
  };
}

export function sampleOverview(overrides: Partial<SeriesOverview> = {}): SeriesOverview {
  return {
    series: [sampleSeries()],
    warnings: [],
    coverage: [
      {
        channelId: CHANNEL_ID,
        displayName: 'OneLiteFeather',
        weeks: [
          { isoWeek: '2026-W45', weekStart: '2026-11-02', shorts: 10, covered: 9, noSeries: 1, percent: 90, flagged: false },
          { isoWeek: '2026-W44', weekStart: '2026-10-26', shorts: 10, covered: 8, noSeries: 0, percent: 80, flagged: true },
          { isoWeek: '2026-W43', weekStart: '2026-10-19', shorts: 0, covered: 0, noSeries: 0, percent: null, flagged: false },
        ],
      },
    ],
    ...overrides,
  };
}

export function sampleDetail(): SeriesDetail {
  return {
    series: sampleSeries(),
    episodes: [
      {
        number: 1,
        postId: 'p-1',
        title: 'Pods in 30 Sekunden',
        format: 'short',
        publishedAt: '2026-11-02T18:00:00Z',
        playlistState: 'in_playlist',
      },
      {
        number: 2,
        postId: 'p-2',
        title: 'Deployments in 30 Sekunden',
        format: 'short',
        publishedAt: '2026-11-03T18:00:00Z',
        playlistState: 'missing',
      },
    ],
  };
}
