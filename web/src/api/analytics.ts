// The content analytics (change add-content-analytics). The API answers with the collected values only; a null value
// means the platform does not provide the metric for those posts. The browser sends its session cookie and never reads
// or stores a token. The formatting and ranking helpers live in web/src/analytics/format.ts.
import { apiBase, type Fetcher } from '../auth/session';
import { problemText } from './series';

/** One metric of a set of posts. value null = not provided by the platform; average is true only for the percentage. */
export interface Metric {
  name: string;
  value: number | null;
  posts: number;
  average: boolean;
}

/** A total, a channel or a format. For channels the key is the channel id and the label its name. */
export interface Aggregate {
  key: string;
  label: string | null;
  posts: number;
  metrics: Metric[];
}

export type CollectionOutcome = 'collected' | 'not_supported' | 'failed';

export interface ChannelCollection {
  channelId: string;
  displayName: string;
  platform: string;
  outcome: CollectionOutcome;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  failureReason: string | null;
}

export interface AnalyticsOverview {
  from: string;
  to: string;
  totals: Aggregate;
  channels: Aggregate[];
  formats: Aggregate[];
  collections: ChannelCollection[];
}

/**
 * One metric of a registered internal source (for example first joins). An estimate is shown as one and never as a
 * measured value; a null value means no value was stored.
 */
export interface InternalMetric {
  source: string;
  metric: string;
  estimate: boolean;
  value: number | null;
  values: number;
}

export interface SeriesWeek {
  isoWeek: string;
  weekStart: string;
  posts: number;
  metrics: Metric[];
  internal: InternalMetric[];
}

/** A series, or the posts that are in no series (seriesId and label null). */
export interface SeriesAnalytics {
  seriesId: string | null;
  label: string | null;
  posts: number;
  metrics: Metric[];
  weeks: SeriesWeek[];
  internal: InternalMetric[];
}

export interface AnalyticsSeries {
  from: string;
  to: string;
  series: SeriesAnalytics[];
}

export type EqualAgeBy = 'hour' | 'weekday';

export interface EqualAgeParams {
  metric: string;
  offsetHours: number;
  by: EqualAgeBy;
  channelId?: string;
}

/** One slot: an hour (0 to 23) or a weekday (1 = Monday to 7 = Sunday). median null = no post has the metric there. */
export interface EqualAgeSlot {
  slot: number;
  posts: number;
  median: number | null;
}

export interface EqualAge {
  metric: string;
  offsetHours: number;
  by: EqualAgeBy;
  timeZone: string;
  channelId: string | null;
  slots: EqualAgeSlot[];
}

export interface PostLatestValue {
  metric: string;
  value: number;
  collectedAt: string;
}

export interface PostSnapshot {
  offsetMinutes: number;
  metric: string;
  value: number | null;
  asOf: string;
  ageMinutes: number;
}

export interface PostAnalytics {
  postId: string;
  channelId: string;
  platform: string;
  format: string;
  title: string;
  url: string | null;
  publishedAt: string;
  removedAt: string | null;
  latest: PostLatestValue[];
  snapshots: PostSnapshot[];
}

/** The query of the period: both days are optional; the server defaults to the last 90 days. */
export function periodQuery(from?: string, to?: string): URLSearchParams {
  const query = new URLSearchParams();
  if (from) {
    query.set('from', from);
  }
  if (to) {
    query.set('to', to);
  }
  return query;
}

/** The query of the equal-age view, with the channel only when one is chosen. */
export function equalAgeQuery(params: EqualAgeParams): URLSearchParams {
  const query = new URLSearchParams({
    metric: params.metric,
    offsetHours: String(params.offsetHours),
    by: params.by,
  });
  if (params.channelId) {
    query.set('channelId', params.channelId);
  }
  return query;
}

async function getJson<T>(path: string, query: URLSearchParams, fetcher: Fetcher, fallback: string): Promise<T> {
  const search = query.toString();
  const response = await fetcher(`${apiBase()}${path}${search ? `?${search}` : ''}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(await problemText(response, fallback));
  }
  return (await response.json()) as T;
}

/** Totals, channels, formats and the collection status of the period. */
export function fetchAnalyticsOverview(
  from?: string,
  to?: string,
  fetcher: Fetcher = fetch,
): Promise<AnalyticsOverview> {
  return getJson<AnalyticsOverview>(
    '/analytics/overview',
    periodQuery(from, to),
    fetcher,
    'Die Analytics konnten nicht geladen werden.',
  );
}

/** The series of the period, already ranked by engaged views, highest first. */
export function fetchAnalyticsSeries(from?: string, to?: string, fetcher: Fetcher = fetch): Promise<AnalyticsSeries> {
  return getJson<AnalyticsSeries>(
    '/analytics/series',
    periodQuery(from, to),
    fetcher,
    'Die Serien-Analytics konnten nicht geladen werden.',
  );
}

/** One metric at one offset after publication, grouped by hour or weekday. */
export function fetchEqualAge(params: EqualAgeParams, fetcher: Fetcher = fetch): Promise<EqualAge> {
  return getJson<EqualAge>(
    '/analytics/equal-age',
    equalAgeQuery(params),
    fetcher,
    'Die Auswertung nach Veröffentlichungszeit konnte nicht geladen werden.',
  );
}

/** The latest values and the snapshots of one external post. */
export function fetchPostAnalytics(id: string, fetcher: Fetcher = fetch): Promise<PostAnalytics> {
  return getJson<PostAnalytics>(
    `/analytics/external-posts/${encodeURIComponent(id)}`,
    new URLSearchParams(),
    fetcher,
    'Die Kennzahlen des Beitrags konnten nicht geladen werden.',
  );
}
