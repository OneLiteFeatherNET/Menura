import { formatCount, formatViewPercent, NOT_AVAILABLE, PLATFORM_LABELS } from '../../domain/format';
import type { AggregateRow } from './types';

const DAY_MS = 86_400_000;
const decimal = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 });

/** Averages are combined as means over posts; the other metrics are counts and are summed. */
const AVERAGE_METRICS = new Set(['avgViewPercent', 'avgWatchSeconds']);

export function isAverageMetric(metricId: string): boolean {
  return AVERAGE_METRICS.has(metricId);
}

/** A value in de-DE. null is "nicht verfügbar", never 0. */
export function formatMetricValue(metricId: string, value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return NOT_AVAILABLE;
  }
  if (metricId === 'avgViewPercent') {
    return formatViewPercent(value);
  }
  if (metricId === 'avgWatchSeconds') {
    return `${decimal.format(value)} s`;
  }
  return formatCount(value);
}

/** The relative change in percent; null when there is no basis for a comparison. */
export function deltaPercent(current: number | null, previous: number | null): number | null {
  if (current === null || previous === null || previous === 0) {
    return null;
  }
  return ((current - previous) / previous) * 100;
}

export function formatDelta(delta: number | null): string {
  if (delta === null) {
    return 'ohne Vergleichswert';
  }
  const sign = delta > 0 ? '+' : '';
  return `${sign}${decimal.format(Math.round(delta * 10) / 10)} %`;
}

/**
 * The total of one metric over rows of one period. Counts are summed; averages are weighted by the number of posts
 * behind each row. No rows give null, so an empty period is not shown as 0.
 */
export function totalOf(metricId: string, rows: AggregateRow[]): number | null {
  if (rows.length === 0) {
    return null;
  }
  if (isAverageMetric(metricId)) {
    const posts = rows.reduce((sum, row) => sum + row.posts, 0);
    return rows.reduce((sum, row) => sum + row.value * row.posts, 0) / posts;
  }
  return rows.reduce((sum, row) => sum + row.value, 0);
}

export interface ComparisonWindows {
  current: { from: string; to: string };
  previous: { from: string; to: string };
}

/** The last seven days against the seven days before them, as ISO instants. */
export function comparisonWindows(now: Date): ComparisonWindows {
  const end = now.toISOString();
  const middle = new Date(now.getTime() - 7 * DAY_MS).toISOString();
  const start = new Date(now.getTime() - 14 * DAY_MS).toISOString();
  return {
    current: { from: middle, to: end },
    previous: { from: start, to: middle },
  };
}

/** "Nicht für TikTok verfügbar", or for two platforms "… für TikTok und Instagram …". */
export function unavailableNote(platforms: string[]): string {
  const labels = platforms.map((platform) => PLATFORM_LABELS[platform as keyof typeof PLATFORM_LABELS] ?? platform);
  if (labels.length === 0) {
    return '';
  }
  const names = labels.length === 1 ? labels[0] : `${labels.slice(0, -1).join(', ')} und ${labels[labels.length - 1]}`;
  return `nicht für ${names} verfügbar`;
}

/** "2026-11-05" becomes "05.11.", the day label of a chart. */
export function dayLabel(key: string): string {
  const [, month, day] = key.split('-');
  return `${day}.${month}.`;
}
