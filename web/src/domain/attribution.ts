import type { JoinSource, WeeklyAttribution } from '../api/types';

/** The sources in the order the server lists them; unanswered comes last. */
export const SOURCE_ORDER: readonly JoinSource[] = [
  'youtube',
  'tiktok',
  'instagram',
  'twitch',
  'friends',
  'other',
  'unanswered',
];

/** Calendar weeks in the data, oldest first. Week ids such as "2026-W45" sort as text. */
export function weeksOf(rows: readonly WeeklyAttribution[]): string[] {
  return [...new Set(rows.map((row) => row.week))].sort();
}

export function latestWeekOf(rows: readonly WeeklyAttribution[]): string | null {
  const weeks = weeksOf(rows);
  return weeks.length === 0 ? null : (weeks[weeks.length - 1] ?? null);
}

export interface WeekSummary {
  week: string;
  /** Measured: all first joins of the week. */
  firstJoins: number;
  /** Measured: first joins that named a source in the in-game question. */
  answered: number;
  /** answered / firstJoins, as the server rounded it. */
  answerRate: number;
  /** Measured, stated by the players. Every source is present; missing ones are 0. */
  bySource: Record<JoinSource, number>;
}

/** The measured summary of one week, or null when the week has no first joins. */
export function summaryOf(rows: readonly WeeklyAttribution[], week: string): WeekSummary | null {
  const row = rows.find((candidate) => candidate.week === week);
  if (!row) {
    return null;
  }
  const bySource = Object.fromEntries(SOURCE_ORDER.map((source) => [source, 0])) as Record<JoinSource, number>;
  for (const entry of row.bySource) {
    bySource[entry.source] = entry.firstJoins;
  }
  return {
    week,
    firstJoins: row.firstJoins,
    answered: row.answered,
    answerRate: row.answerRate,
    bySource,
  };
}

export interface ChartLayout {
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  bottom: number;
}

export interface Segment {
  week: string;
  source: JoinSource;
  value: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * One stacked column per week, one segment per stated source with joins. Heights scale to the largest week total
 * (at least 1, so an empty chart still has a baseline).
 */
export function stackedColumns(
  weeks: readonly string[],
  rows: readonly WeeklyAttribution[],
  layout: ChartLayout,
): { segments: Segment[]; maxValue: number } {
  const summaries = new Map(weeks.map((week) => [week, summaryOf(rows, week)]));
  const totals = [...summaries.values()].map((summary) => summary?.firstJoins ?? 0);
  const maxValue = Math.max(1, ...totals);
  const plotWidth = layout.width - layout.left - layout.right;
  const plotHeight = layout.height - layout.top - layout.bottom;
  const groupWidth = weeks.length === 0 ? 0 : plotWidth / weeks.length;
  const columnWidth = groupWidth * 0.6;

  const segments: Segment[] = [];
  weeks.forEach((week, weekIndex) => {
    const summary = summaries.get(week);
    let baseline = layout.top + plotHeight;
    for (const source of SOURCE_ORDER) {
      const value = summary?.bySource[source] ?? 0;
      if (value === 0) {
        continue;
      }
      const height = (value / maxValue) * plotHeight;
      segments.push({
        week,
        source,
        value,
        x: layout.left + weekIndex * groupWidth + groupWidth * 0.2,
        y: baseline - height,
        width: columnWidth,
        height,
      });
      baseline -= height;
    }
  });
  return { segments, maxValue };
}
