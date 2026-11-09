import { describe, expect, it, vi } from 'vitest';
import {
  fetchAnalyticsOverview,
  fetchAnalyticsSeries,
  fetchEqualAge,
  fetchPostAnalytics,
  type Metric,
} from '../src/api/analytics';
import {
  DEFAULT_PERIOD_DAYS,
  NO_INTERNAL_TEXT,
  NOT_AVAILABLE,
  barFraction,
  dateLabel,
  formatMetricValue,
  internalMetricText,
  internalMetricsText,
  maxValue,
  metricOf,
  outcomeLabel,
  periodRange,
  rankSeries,
  slotLabel,
} from '../src/analytics/format';
import { sampleMetrics, sampleSeriesRow } from '../src/analytics/samples';

const NOW = new Date('2026-11-08T12:00:00Z');

function metric(overrides: Partial<Metric>): Metric {
  return { name: 'views', value: 0, posts: 1, average: false, ...overrides };
}

function answer(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('metric values', () => {
  it('shows not available for a metric the platform does not provide', () => {
    expect(formatMetricValue(metric({ value: null, posts: 0 }))).toBe(NOT_AVAILABLE);
  });

  it('shows not available for a metric that is missing from the list', () => {
    expect(formatMetricValue(undefined)).toBe(NOT_AVAILABLE);
  });

  it('shows the average view percentage with a percent sign', () => {
    expect(formatMetricValue(metric({ name: 'average_view_percentage', value: 46.5, average: true }))).toBe('46,5 %');
  });

  it('groups the thousands of a count in German notation', () => {
    expect(formatMetricValue(metric({ value: 4200 }))).toBe('4.200');
  });
});

describe('period', () => {
  it('starts the default period 89 days before today, so that it spans 90 days', () => {
    expect(periodRange(NOW, DEFAULT_PERIOD_DAYS)).toEqual({ from: '2026-08-11', to: '2026-11-08' });
  });

  it('spans the given number of days including both ends', () => {
    expect(periodRange(NOW, 7)).toEqual({ from: '2026-11-02', to: '2026-11-08' });
  });

  it('counts the day in UTC, so a late evening in another zone belongs to the next UTC day', () => {
    expect(periodRange(new Date('2026-11-08T23:30:00-05:00'), 1)).toEqual({ from: '2026-11-09', to: '2026-11-09' });
  });

  it('formats a calendar day in German notation', () => {
    expect(dateLabel('2026-08-11')).toBe('11.08.2026');
  });
});

describe('series ranking', () => {
  it('orders the series by engaged views, highest first', () => {
    const ranked = rankSeries([
      sampleSeriesRow({ label: 'Klein', metrics: sampleMetrics({ engaged_views: 10 }) }),
      sampleSeriesRow({ label: 'Groß', metrics: sampleMetrics({ engaged_views: 900 }) }),
    ]);

    expect(ranked.map((series) => series.label)).toEqual(['Groß', 'Klein']);
  });

  it('places a series without engaged views after every series with a value', () => {
    const ranked = rankSeries([
      sampleSeriesRow({ label: 'Ohne Wert', metrics: sampleMetrics({ engaged_views: null }) }),
      sampleSeriesRow({ label: 'Mit Wert', metrics: sampleMetrics({ engaged_views: 1 }) }),
    ]);

    expect(ranked.map((series) => series.label)).toEqual(['Mit Wert', 'Ohne Wert']);
  });

  it('keeps the order of the server for series with the same engaged views', () => {
    const ranked = rankSeries([
      sampleSeriesRow({ label: 'Erste', metrics: sampleMetrics({ engaged_views: 300 }) }),
      sampleSeriesRow({ label: 'Zweite', metrics: sampleMetrics({ engaged_views: 300 }) }),
    ]);

    expect(ranked.map((series) => series.label)).toEqual(['Erste', 'Zweite']);
  });

  it('does not reorder the list it was given', () => {
    const input = [
      sampleSeriesRow({ label: 'Klein', metrics: sampleMetrics({ engaged_views: 10 }) }),
      sampleSeriesRow({ label: 'Groß', metrics: sampleMetrics({ engaged_views: 900 }) }),
    ];

    rankSeries(input);

    expect(input.map((series) => series.label)).toEqual(['Klein', 'Groß']);
  });
});

describe('internal metrics', () => {
  it('marks an estimate with the word estimated, never as a measured value', () => {
    const text = internalMetricText({ source: 'joins', metric: 'first_joins', estimate: true, value: 12, values: 3 });

    expect(text).toBe('Erste Beitritte: 12 (geschätzt)');
  });

  it('shows a measured value without the estimate mark', () => {
    const text = internalMetricText({ source: 'joins', metric: 'first_joins', estimate: false, value: 7, values: 2 });

    expect(text).toBe('Erste Beitritte: 7');
  });

  it('shows a value that was never stored as not available, not as zero', () => {
    const text = internalMetricText({ source: 'joins', metric: 'first_joins', estimate: false, value: null, values: 0 });

    expect(text).toBe(`Erste Beitritte: ${NOT_AVAILABLE}`);
  });

  it('shows the dash for a series without internal metrics', () => {
    expect(internalMetricsText([])).toBe(NO_INTERNAL_TEXT);
  });

  it('keeps the name of an internal metric the client does not know', () => {
    const text = internalMetricText({ source: 'x', metric: 'new_metric', estimate: false, value: 1, values: 1 });

    expect(text).toBe('new_metric: 1');
  });
});

describe('bars', () => {
  it('gives a missing value no bar', () => {
    expect(barFraction(null, 1000)).toBe(0);
  });

  it('scales a value to its share of the largest value', () => {
    expect(barFraction(500, 1000)).toBe(0.5);
  });

  it('finds the largest value and ignores missing values', () => {
    expect(maxValue([null, 3, 7])).toBe(7);
  });
});

describe('slots and labels', () => {
  it('names an hour with two digits and the word Uhr', () => {
    expect(slotLabel('hour', 9)).toBe('09 Uhr');
  });

  it('names slot 1 as Monday', () => {
    expect(slotLabel('weekday', 1)).toBe('Montag');
  });

  it('names slot 7 as Sunday', () => {
    expect(slotLabel('weekday', 7)).toBe('Sonntag');
  });

  it('finds a metric by its name', () => {
    expect(metricOf(sampleMetrics({ likes: 12 }), 'likes')?.value).toBe(12);
  });

  it('names a collection that the platform does not support', () => {
    expect(outcomeLabel('not_supported')).toBe('Nicht unterstützt');
  });
});

describe('analytics client', () => {
  it('sends the period as from and to query parameters', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({ from: '2026-08-11', to: '2026-11-08' }));

    await fetchAnalyticsOverview('2026-08-11', '2026-11-08', fetcher);

    expect(String(fetcher.mock.calls[0]?.[0])).toMatch(/\/analytics\/overview\?from=2026-08-11&to=2026-11-08$/);
  });

  it('sends no period when none is chosen, so the server applies its default', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({}));

    await fetchAnalyticsOverview(undefined, undefined, fetcher);

    expect(String(fetcher.mock.calls[0]?.[0])).toMatch(/\/analytics\/overview$/);
  });

  it('sends the session cookie and no authorization header', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({ from: '2026-08-11', to: '2026-11-08', series: [] }));

    await fetchAnalyticsSeries('2026-08-11', '2026-11-08', fetcher);

    const init = fetcher.mock.calls[0]?.[1] as RequestInit;
    expect(init.credentials, 'the session cookie travels with the request').toBe('include');
    expect(init.headers, 'no authorization header is set by the browser client').not.toHaveProperty('Authorization');
  });

  it('sends the equal-age choice without a channel when none is chosen', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({}));

    await fetchEqualAge({ metric: 'views', offsetHours: 24, by: 'hour' }, fetcher);

    expect(String(fetcher.mock.calls[0]?.[0])).toMatch(/\/analytics\/equal-age\?metric=views&offsetHours=24&by=hour$/);
  });

  it('sends the channel of the equal-age choice when one is chosen', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({}));

    await fetchEqualAge({ metric: 'likes', offsetHours: 168, by: 'weekday', channelId: 'c-1' }, fetcher);

    expect(String(fetcher.mock.calls[0]?.[0])).toContain('channelId=c-1');
  });

  it('shows the reason the server gives for a refused request', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({ detail: 'Das Datum liegt in der Zukunft.' }, 400));

    await expect(fetchAnalyticsOverview('2030-01-01', '2030-01-02', fetcher)).rejects.toThrow(
      'Das Datum liegt in der Zukunft.',
    );
  });

  it('shows the fixed sentence when a failed answer has no problem details', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => new Response('Bad Gateway', { status: 502 }));

    await expect(fetchPostAnalytics('p-1', fetcher)).rejects.toThrow(
      'Die Kennzahlen des Beitrags konnten nicht geladen werden.',
    );
  });

  it('encodes the post id in the path', async () => {
    const fetcher = vi.fn(async (_url: string, _init?: RequestInit) => answer({}));

    await fetchPostAnalytics('p/1', fetcher);

    expect(String(fetcher.mock.calls[0]?.[0])).toMatch(/\/analytics\/external-posts\/p%2F1$/);
  });
});
