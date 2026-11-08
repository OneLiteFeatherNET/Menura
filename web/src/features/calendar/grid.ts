import { berlinDateKey, berlinHour } from './dates';
import type { Publication } from './types';

/** The hours of the week grid: 08:00 to 23:xx Berlin. */
export const WEEK_FIRST_HOUR = 8;
export const WEEK_LAST_HOUR = 23;
export const WEEK_HOURS: number[] = Array.from(
  { length: WEEK_LAST_HOUR - WEEK_FIRST_HOUR + 1 },
  (_, index) => WEEK_FIRST_HOUR + index,
);

/** Entries of one month cell shown before the "+n weitere" link. */
export const MONTH_VISIBLE_ENTRIES = 3;

export function byPublishTime(a: Publication, b: Publication): number {
  const difference = Date.parse(a.publishAt) - Date.parse(b.publishAt);
  return difference !== 0 ? difference : a.id.localeCompare(b.id);
}

/** Publications grouped by their Berlin day, each group oldest first. */
export function groupByBerlinDay(publications: Publication[]): Map<string, Publication[]> {
  const groups = new Map<string, Publication[]>();
  for (const publication of [...publications].sort(byPublishTime)) {
    const key = berlinDateKey(new Date(publication.publishAt));
    const group = groups.get(key);
    if (group) {
      group.push(publication);
    } else {
      groups.set(key, [publication]);
    }
  }
  return groups;
}

/** The week cell key of an entry: its Berlin day and its Berlin hour. */
export function weekCellKey(day: string, hour: number): string {
  return `${day}|${hour}`;
}

/** Entries placed in the week grid, by cell; entries outside the grid hours are in {@link outsideGridHours}. */
export function placeInWeek(publications: Publication[], days: string[]): Map<string, Publication[]> {
  const cells = new Map<string, Publication[]>();
  for (const publication of [...publications].sort(byPublishTime)) {
    const at = new Date(publication.publishAt);
    const day = berlinDateKey(at);
    const hour = berlinHour(at);
    if (!days.includes(day) || hour < WEEK_FIRST_HOUR || hour > WEEK_LAST_HOUR) {
      continue;
    }
    const key = weekCellKey(day, hour);
    const cell = cells.get(key);
    if (cell) {
      cell.push(publication);
    } else {
      cells.set(key, [publication]);
    }
  }
  return cells;
}

/** Entries of the given days that fall outside the grid hours, oldest first. */
export function outsideGridHours(publications: Publication[], days: string[]): Publication[] {
  return [...publications]
    .sort(byPublishTime)
    .filter((publication) => {
      const at = new Date(publication.publishAt);
      const hour = berlinHour(at);
      return days.includes(berlinDateKey(at)) && (hour < WEEK_FIRST_HOUR || hour > WEEK_LAST_HOUR);
    });
}
