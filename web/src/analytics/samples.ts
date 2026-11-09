// Sample analytics data for the stories and the tests. It is data only: no story is registered from this file.
import type {
  AnalyticsOverview,
  AnalyticsSeries,
  Aggregate,
  ChannelCollection,
  EqualAge,
  EqualAgeBy,
  Metric,
  SeriesAnalytics,
} from '../api/analytics';
import { METRIC_ORDER } from './format';

/** The seven metrics in order. Values not given are null (not provided) with the given post count. */
export function sampleMetrics(values: Partial<Record<string, number | null>> = {}, posts = 4): Metric[] {
  return METRIC_ORDER.map((name) => {
    const given = values[name];
    return { name, value: given === undefined ? 0 : given, posts, average: name === 'average_view_percentage' };
  });
}

/** The metrics of a platform without engaged views, watch time and the percentage: those are null. */
export function sampleMetricsWithoutEngagement(posts = 4): Metric[] {
  return sampleMetrics({ views: 1240, engaged_views: null, average_view_percentage: null, watch_time: null, likes: 58, comments: 7, shares: null }, posts);
}

export function sampleAggregate(key: string, label: string | null, values: Partial<Record<string, number | null>>, posts = 4): Aggregate {
  return { key, label, posts, metrics: sampleMetrics(values, posts) };
}

export const CHANNEL_ID = 'c0ffee00-0000-4000-8000-000000000001';
export const SECOND_CHANNEL_ID = 'c0ffee00-0000-4000-8000-000000000002';

export function sampleChannels(): Aggregate[] {
  return [
    sampleAggregate(CHANNEL_ID, 'OneLiteFeather', { views: 4200, engaged_views: 1900, average_view_percentage: 46.5, watch_time: 310, likes: 180, comments: 22, shares: 9 }, 12),
    sampleAggregate(SECOND_CHANNEL_ID, 'Menura Clips', { views: 980, engaged_views: null, average_view_percentage: null, watch_time: null, likes: 41, comments: 3, shares: null }, 5),
  ];
}

export function sampleCollections(): ChannelCollection[] {
  return [
    {
      channelId: CHANNEL_ID,
      displayName: 'OneLiteFeather',
      platform: 'youtube',
      outcome: 'collected',
      lastSuccessAt: '2026-11-07T06:00:00Z',
      lastFailureAt: null,
      failureReason: null,
    },
    {
      channelId: SECOND_CHANNEL_ID,
      displayName: 'Menura Clips',
      platform: 'tiktok',
      outcome: 'not_supported',
      lastSuccessAt: null,
      lastFailureAt: null,
      failureReason: null,
    },
  ];
}

export function sampleOverview(overrides: Partial<AnalyticsOverview> = {}): AnalyticsOverview {
  const channels = sampleChannels();
  return {
    from: '2026-08-11',
    to: '2026-11-08',
    totals: sampleAggregate('total', null, { views: 5180, engaged_views: 1900, average_view_percentage: 46.5, watch_time: 310, likes: 221, comments: 25, shares: 9 }, 17),
    channels,
    formats: [sampleAggregate('short', 'short', { views: 3100 }, 9), sampleAggregate('video', 'video', { views: 2080 }, 8)],
    collections: sampleCollections(),
    ...overrides,
  };
}

/** An overview of a period without posts: every metric has no value and no post. */
export function sampleEmptyOverview(): AnalyticsOverview {
  const empty = (key: string, label: string | null): Aggregate => ({
    key,
    label,
    posts: 0,
    metrics: METRIC_ORDER.map((name) => ({ name, value: null, posts: 0, average: name === 'average_view_percentage' })),
  });
  return {
    from: '2026-08-11',
    to: '2026-11-08',
    totals: empty('total', null),
    channels: [],
    formats: [],
    collections: [],
  };
}

export function sampleSeriesRow(overrides: Partial<SeriesAnalytics> = {}): SeriesAnalytics {
  return {
    seriesId: 'a1b2c3d4-0000-4000-8000-000000000001',
    label: 'Kubernetes in 30 s',
    posts: 6,
    metrics: sampleMetrics({ views: 2100, engaged_views: 950, average_view_percentage: 52.1, likes: 90 }, 6),
    weeks: [],
    internal: [],
    ...overrides,
  };
}

/** A series with first joins of the platform's estimate, one with a measured value and one without a stored value. */
export function sampleSeriesWithInternal(): AnalyticsSeries {
  return {
    from: '2026-08-11',
    to: '2026-11-08',
    series: [
      sampleSeriesRow({
        internal: [
          { source: 'joins', metric: 'first_joins', estimate: true, value: 12, values: 3 },
          { source: 'joins', metric: 'first_joins', estimate: false, value: null, values: 0 },
        ],
      }),
    ],
  };
}

/** Three series in the order of the server, which ranks them by engaged views. */
export function sampleSeries(): AnalyticsSeries {
  return {
    from: '2026-08-11',
    to: '2026-11-08',
    series: [
      sampleSeriesRow(),
      sampleSeriesRow({
        seriesId: 'a1b2c3d4-0000-4000-8000-000000000002',
        label: 'Deployments in 30 s',
        posts: 4,
        metrics: sampleMetrics({ views: 1100, engaged_views: 300, average_view_percentage: 38.4, likes: 20 }, 4),
      }),
      sampleSeriesRow({
        seriesId: null,
        label: null,
        posts: 2,
        metrics: sampleMetrics({ views: 400, engaged_views: null, average_view_percentage: null, likes: 5 }, 2),
      }),
    ],
  };
}

/** A series answer in which the platform gives no engaged views for any series. */
export function sampleSeriesWithoutEngagement(): AnalyticsSeries {
  return {
    from: '2026-08-11',
    to: '2026-11-08',
    series: [
      sampleSeriesRow({
        metrics: sampleMetrics({ views: 900, engaged_views: null, average_view_percentage: null, likes: 12 }, 3),
        posts: 3,
      }),
    ],
  };
}

/** The equal-age answer: the posts and the median of each slot. Slots without a post have posts 0. */
export function sampleEqualAge(by: EqualAgeBy = 'hour', metric = 'views'): EqualAge {
  const slotCount = by === 'hour' ? 24 : 7;
  const firstSlot = by === 'hour' ? 0 : 1;
  const slots = Array.from({ length: slotCount }, (_, index) => {
    const slot = firstSlot + index;
    const active = by === 'hour' ? slot >= 9 && slot <= 19 : slot <= 5;
    return {
      slot,
      posts: active ? 2 : 0,
      median: active ? 800 + slot * 40 : null,
    };
  });
  return { metric, offsetHours: 24, by, timeZone: 'Europe/Berlin', channelId: null, slots };
}

/** The equal-age answer of a metric that the platform does not provide for these posts. */
export function sampleEqualAgeNotSupported(by: EqualAgeBy = 'weekday'): EqualAge {
  const slotCount = by === 'hour' ? 24 : 7;
  const firstSlot = by === 'hour' ? 0 : 1;
  return {
    metric: 'likes',
    offsetHours: 24,
    by,
    timeZone: 'Europe/Berlin',
    channelId: null,
    slots: Array.from({ length: slotCount }, (_, index) => ({ slot: firstSlot + index, posts: 2, median: null })),
  };
}
