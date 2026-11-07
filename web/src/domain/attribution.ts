import type { Platform, WeeklyAttribution } from '../api/types';

export const PLATFORM_ORDER: readonly Platform[] = ['youtube', 'tiktok', 'instagram', 'direct'];

/** Calendar weeks in the data, oldest first. Week ids such as "2026-W45" sort as text. */
export function weeksOf(rows: readonly WeeklyAttribution[]): string[] {
  return [...new Set(rows.map((row) => row.week))].sort();
}

export function latestWeekOf(rows: readonly WeeklyAttribution[]): string | null {
  const weeks = weeksOf(rows);
  return weeks.length === 0 ? null : (weeks[weeks.length - 1] ?? null);
}

/** Measured first joins of one week, summed over channels per platform. Missing platforms are 0. */
export function totalsByPlatform(
  rows: readonly WeeklyAttribution[],
  week: string,
): Record<Platform, number> {
  const totals: Record<Platform, number> = { youtube: 0, tiktok: 0, instagram: 0, direct: 0 };
  for (const row of rows) {
    if (row.week === week) {
      totals[row.platform] += row.firstJoins;
    }
  }
  return totals;
}

export interface Bar {
  week: string;
  platform: Platform;
  value: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ChartLayout {
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  bottom: number;
}

/**
 * Bars for a grouped column chart: one group per week, one bar per platform. Heights scale to the
 * largest value (at least 1, so an empty chart still has a baseline).
 */
export function groupedBars(
  weeks: readonly string[],
  rows: readonly WeeklyAttribution[],
  layout: ChartLayout,
): { bars: Bar[]; maxValue: number } {
  const perWeek = new Map(weeks.map((week) => [week, totalsByPlatform(rows, week)]));
  const maxValue = Math.max(1, ...[...perWeek.values()].flatMap((totals) => Object.values(totals)));
  const plotWidth = layout.width - layout.left - layout.right;
  const plotHeight = layout.height - layout.top - layout.bottom;
  const groupWidth = weeks.length === 0 ? 0 : plotWidth / weeks.length;
  const barWidth = (groupWidth * 0.7) / PLATFORM_ORDER.length;

  const bars: Bar[] = [];
  weeks.forEach((week, weekIndex) => {
    const totals = perWeek.get(week) ?? totalsByPlatform(rows, week);
    PLATFORM_ORDER.forEach((platform, platformIndex) => {
      const value = totals[platform];
      const height = (value / maxValue) * plotHeight;
      bars.push({
        week,
        platform,
        value,
        x: layout.left + weekIndex * groupWidth + groupWidth * 0.15 + platformIndex * barWidth,
        y: layout.top + plotHeight - height,
        width: barWidth,
        height,
      });
    });
  });
  return { bars, maxValue };
}
