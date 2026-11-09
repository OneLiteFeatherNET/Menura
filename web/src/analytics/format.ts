// Pure helpers of the analytics views: German labels, the text of a metric value, the period and the ranking. None of
// them reads the system clock or the network; the caller passes the date and the data.
import { WEEKDAY_LABELS } from '../api/series';
import type {
  CollectionOutcome,
  EqualAgeBy,
  InternalMetric,
  Metric,
  SeriesAnalytics,
} from '../api/analytics';

/** What a metric shows when the platform does not provide it. A missing value is never shown as 0. */
export const NOT_AVAILABLE = 'nicht verfügbar';

/** The seven metrics in the order the server sends them. */
export const METRIC_ORDER = [
  'views',
  'engaged_views',
  'average_view_percentage',
  'watch_time',
  'likes',
  'comments',
  'shares',
] as const;

export const METRIC_LABELS: Record<string, string> = {
  views: 'Aufrufe',
  engaged_views: 'Engagierte Aufrufe',
  average_view_percentage: 'Ø Anteil angesehen',
  watch_time: 'Wiedergabezeit',
  likes: 'Likes',
  comments: 'Kommentare',
  shares: 'Geteilt',
};

export const OUTCOME_LABELS: Record<CollectionOutcome, string> = {
  collected: 'Erfasst',
  not_supported: 'Nicht unterstützt',
  failed: 'Fehlgeschlagen',
};

const numberFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 });

/** A number in German notation, with at most one decimal. */
export function formatNumber(value: number): string {
  return numberFormat.format(value);
}

/** The text of a metric: the value, a percent sign for the average percentage, or 'nicht verfügbar' for null. */
export function formatMetricValue(metric: Metric | undefined): string {
  if (!metric || metric.value === null) {
    return NOT_AVAILABLE;
  }
  const text = formatNumber(metric.value);
  return metric.average ? `${text} %` : text;
}

export function metricLabel(name: string): string {
  return METRIC_LABELS[name] ?? name;
}

/** The German names of the internal metrics the server knows; any other name is shown as it is sent. */
export const INTERNAL_METRIC_LABELS: Record<string, string> = {
  first_joins: 'Erste Beitritte',
};

/** The mark of an internal value the source reports as an estimate. */
export const ESTIMATE_TEXT = 'geschätzt';

/** What a series without internal values shows. */
export const NO_INTERNAL_TEXT = '–';

/**
 * One internal metric as text: its name, its value and, for an estimate, the mark 'geschätzt'. A value that was never
 * stored reads 'nicht verfügbar', never 0.
 */
export function internalMetricText(metric: InternalMetric): string {
  const name = INTERNAL_METRIC_LABELS[metric.metric] ?? metric.metric;
  const value = metric.value === null ? NOT_AVAILABLE : formatNumber(metric.value);
  return metric.estimate ? `${name}: ${value} (${ESTIMATE_TEXT})` : `${name}: ${value}`;
}

/** All internal metrics of a series as one text, or the dash when there are none. */
export function internalMetricsText(metrics: readonly InternalMetric[]): string {
  return metrics.length === 0 ? NO_INTERNAL_TEXT : metrics.map(internalMetricText).join(', ');
}

/** The metric with this name, or undefined when the list does not contain it. */
export function metricOf(metrics: readonly Metric[], name: string): Metric | undefined {
  return metrics.find((metric) => metric.name === name);
}

/** A collection status in German. */
export function outcomeLabel(outcome: CollectionOutcome): string {
  return OUTCOME_LABELS[outcome];
}

/** A time in German notation, or a dash when there is none. */
export function timestampLabel(value: string | null): string {
  return value ? new Date(value).toLocaleString('de-DE') : '—';
}

/** A calendar day of the API (YYYY-MM-DD) in German notation (DD.MM.YYYY). */
export function dateLabel(isoDay: string): string {
  const [year, month, day] = isoDay.split('-');
  return `${day}.${month}.${year}`;
}

/** The period that is shown when the person chooses none. */
export const DEFAULT_PERIOD_DAYS = 90;

export const PERIOD_OPTIONS = [7, 30, 90, 365].map((days) => ({ value: String(days), label: `Letzte ${days} Tage` }));

const DAY_MS = 86_400_000;

/**
 * The period of the last `days` days, both ends included, counted in UTC from the given moment. The day of `now` is the
 * last day of the period.
 */
export function periodRange(now: Date, days = DEFAULT_PERIOD_DAYS): { from: string; to: string } {
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return {
    from: new Date(today - (days - 1) * DAY_MS).toISOString().slice(0, 10),
    to: new Date(today).toISOString().slice(0, 10),
  };
}

function engagedValue(series: SeriesAnalytics): number | null {
  return metricOf(series.metrics, 'engaged_views')?.value ?? null;
}

/** The series ranked by engaged views, highest first; a series without a value comes last. A copy is returned. */
export function rankSeries(series: readonly SeriesAnalytics[]): SeriesAnalytics[] {
  return [...series].sort((left, right) => {
    const a = engagedValue(left);
    const b = engagedValue(right);
    if (a === b) {
      return 0;
    }
    if (a === null) {
      return 1;
    }
    if (b === null) {
      return -1;
    }
    return b - a;
  });
}

/** The largest value of a list; null values and an empty list count as 0. */
export function maxValue(values: readonly (number | null)[]): number {
  return values.reduce<number>((max, value) => (value === null ? max : Math.max(max, value)), 0);
}

/** The length of a bar as a share of the largest value, from 0 to 1. A null value has no bar. */
export function barFraction(value: number | null, max: number): number {
  if (value === null || max <= 0) {
    return 0;
  }
  return Math.min(1, Math.max(0, value / max));
}

/** The shown empty state of a period without posts (the first collection has not run yet). */
export const NO_POSTS_TITLE = 'Noch keine Kennzahlen';
export const NO_POSTS_TEXT =
  'Im gewählten Zeitraum gibt es keine Beiträge. Kennzahlen erscheinen nach der ersten Erfassung durch die Plattform.';

/** The choice of the equal-age view: the grouping, the offset after publication and the metric. */
export interface EqualAgeChoice {
  by: EqualAgeBy;
  offsetHours: number;
  metric: string;
}

export const EQUAL_AGE_OFFSETS = [
  { value: '1', label: '+1 h' },
  { value: '2', label: '+2 h' },
  { value: '4', label: '+4 h' },
  { value: '8', label: '+8 h' },
  { value: '24', label: '+24 h' },
  { value: '48', label: '+48 h' },
  { value: '168', label: '+7 Tage' },
];

export const EQUAL_AGE_METRICS = [
  { value: 'views', label: 'Aufrufe' },
  { value: 'engaged_views', label: 'Engagierte Aufrufe' },
  { value: 'likes', label: 'Likes' },
];

export const EQUAL_AGE_GROUPS = [
  { value: 'hour', label: 'Nach Uhrzeit' },
  { value: 'weekday', label: 'Nach Wochentag' },
];

/** The name of a slot: the hour of the day in Europe/Berlin, or the weekday (slot 1 is Monday). */
export function slotLabel(by: EqualAgeBy, slot: number): string {
  if (by === 'hour') {
    return `${String(slot).padStart(2, '0')} Uhr`;
  }
  return WEEKDAY_LABELS[slot - 1] ?? String(slot);
}
