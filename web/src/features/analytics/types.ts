/** Bodies of the analytics API (GET /api/prototype/analytics/...). Field names are fixed by the server. */

export const OFFSETS = ['+1h', '+2h', '+4h', '+8h', '+24h', '+48h', '+7d'] as const;
export type OffsetLabel = (typeof OFFSETS)[number];

export const WEEKDAYS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;
export type Weekday = (typeof WEEKDAYS)[number];

export type GroupBy = 'day' | 'week' | 'channel' | 'series' | 'publishHour' | 'weekday';

/** One catalogue entry: the platforms that deliver the metric through their API. */
export interface Metric {
  id: string;
  label: string;
  platforms: string[];
}

export interface AggregateRow {
  key: string;
  value: number;
  posts: number;
}

export interface AggregateResult {
  metric: string;
  groupBy: GroupBy;
  offset: OffsetLabel;
  rows: AggregateRow[];
  /** Selected channels whose platform does not deliver the metric. They are never shown as 0. */
  missing: string[];
}

export interface HeatmapCell {
  weekday: Weekday;
  hour: number;
  value: number;
  posts: number;
}

export interface Heatmap {
  cells: HeatmapCell[];
  missing: string[];
}

/** One measurement of a post. Engaged views are null where the platform does not deliver them. */
export interface PostSnapshot {
  offset: OffsetLabel;
  at: string;
  views: number;
  engagedViews: number | null;
  likes: number;
  comments: number;
  shares: number;
}

export interface AggregateQuery {
  metric: string;
  channelIds?: string[];
  groupBy?: GroupBy;
  offset?: OffsetLabel;
  from?: string;
  to?: string;
}

export interface HeatmapQuery {
  metric: string;
  channelIds?: string[];
  offset?: OffsetLabel;
}
