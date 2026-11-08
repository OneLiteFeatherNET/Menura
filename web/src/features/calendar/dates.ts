/**
 * Date math of the calendar. Every instant is shown and grouped in Europe/Berlin, computed through Intl. Day keys
 * are plain "YYYY-MM-DD" strings; arithmetic on them is done in UTC, which has no DST, so it cannot shift a day.
 */

export const BERLIN = 'Europe/Berlin';

export type CalendarTab = 'monat' | 'woche' | 'liste';

export interface DayRange {
  /** First day, inclusive. */
  fromKey: string;
  /** Day after the last one, exclusive. */
  toKey: string;
}

const MILLIS_PER_DAY = 86_400_000;

const partsFormat = new Intl.DateTimeFormat('en-US', {
  timeZone: BERLIN,
  hourCycle: 'h23',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const dayHeading = new Intl.DateTimeFormat('de-DE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
});

const monthHeading = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' });

const shortDay = new Intl.DateTimeFormat('de-DE', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });

const berlinDateTime = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: BERLIN,
});

interface BerlinParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function keyOf(year: number, month: number, day: number): string {
  return `${year}-${pad(month)}-${pad(day)}`;
}

function parseKey(key: string): { year: number; month: number; day: number } {
  const [year, month, day] = key.split('-').map(Number);
  return { year: year ?? 1970, month: month ?? 1, day: day ?? 1 };
}

function berlinParts(date: Date): BerlinParts {
  const values: Record<string, string> = {};
  for (const part of partsFormat.formatToParts(date)) {
    values[part.type] = part.value;
  }
  return {
    year: Number(values.year),
    month: Number(values.month),
    day: Number(values.day),
    hour: Number(values.hour),
    minute: Number(values.minute),
  };
}

/** Berlin's offset from UTC in minutes at the given instant (120 in summer, 60 in winter). */
function berlinOffsetMinutes(date: Date): number {
  const parts = berlinParts(date);
  const wallClockAsUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute);
  const wholeMinute = Math.floor(date.getTime() / 60_000) * 60_000;
  return Math.round((wallClockAsUtc - wholeMinute) / 60_000);
}

/** The Berlin calendar day of an instant, as "YYYY-MM-DD". */
export function berlinDateKey(date: Date): string {
  const parts = berlinParts(date);
  return keyOf(parts.year, parts.month, parts.day);
}

export function berlinHour(date: Date): number {
  return berlinParts(date).hour;
}

/** The Berlin wall-clock time of an instant, "HH:mm". */
export function berlinTime(date: Date): string {
  const parts = berlinParts(date);
  return `${pad(parts.hour)}:${pad(parts.minute)}`;
}

/** The instant of a Berlin wall-clock time on a day. Summer and winter time are both handled. */
export function zonedToDate(dayKey: string, time: string): Date {
  const { year, month, day } = parseKey(dayKey);
  const [hour = 0, minute = 0] = time.split(':').map(Number);
  const wallClockAsUtc = Date.UTC(year, month - 1, day, hour, minute);
  const firstGuess = wallClockAsUtc - berlinOffsetMinutes(new Date(wallClockAsUtc)) * 60_000;
  const secondGuess = wallClockAsUtc - berlinOffsetMinutes(new Date(firstGuess)) * 60_000;
  return new Date(secondGuess);
}

export function addDays(dayKey: string, days: number): string {
  const { year, month, day } = parseKey(dayKey);
  const shifted = new Date(Date.UTC(year, month - 1, day) + days * MILLIS_PER_DAY);
  return keyOf(shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate());
}

/** 0 for Monday through 6 for Sunday. */
export function weekdayIndex(dayKey: string): number {
  const { year, month, day } = parseKey(dayKey);
  return (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7;
}

export function mondayOf(dayKey: string): string {
  return addDays(dayKey, -weekdayIndex(dayKey));
}

/** The seven days of the week that contains the anchor, Monday first. */
export function weekDays(anchorKey: string): string[] {
  const monday = mondayOf(anchorKey);
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
}

/** Six weeks of the month of the anchor, from the Monday on or before the first day. */
export function monthGrid(anchorKey: string): string[] {
  const { year, month } = parseKey(anchorKey);
  const monday = mondayOf(keyOf(year, month, 1));
  return Array.from({ length: 42 }, (_, index) => addDays(monday, index));
}

export function firstOfMonth(anchorKey: string): string {
  const { year, month } = parseKey(anchorKey);
  return keyOf(year, month, 1);
}

export function monthOf(dayKey: string): string {
  return dayKey.slice(0, 7);
}

export function dayNumber(dayKey: string): number {
  return parseKey(dayKey).day;
}

/** The days a view loads: the week, the six-week grid of the month, or the calendar month for the list. */
export function periodRange(view: CalendarTab, anchorKey: string): DayRange {
  if (view === 'woche') {
    const fromKey = mondayOf(anchorKey);
    return { fromKey, toKey: addDays(fromKey, 7) };
  }
  if (view === 'monat') {
    const grid = monthGrid(anchorKey);
    return { fromKey: grid[0] ?? anchorKey, toKey: addDays(grid[41] ?? anchorKey, 1) };
  }
  const first = firstOfMonth(anchorKey);
  return { fromKey: first, toKey: addMonths(first, 1) };
}

/** The UTC instants of a day range: Berlin midnight at both ends, the end exclusive. */
export function rangeToInstants(range: DayRange): { from: string; to: string } {
  return {
    from: zonedToDate(range.fromKey, '00:00').toISOString(),
    to: zonedToDate(range.toKey, '00:00').toISOString(),
  };
}

function addMonths(dayKey: string, months: number): string {
  const { year, month, day } = parseKey(dayKey);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  return keyOf(target.getUTCFullYear(), target.getUTCMonth() + 1, Math.min(day, lastDay));
}

/** Moves the anchor by one week (week view) or one month (month and list views). */
export function shiftAnchor(view: CalendarTab, anchorKey: string, direction: -1 | 1): string {
  return view === 'woche' ? addDays(anchorKey, 7 * direction) : addMonths(anchorKey, direction);
}

/** The heading of the period: "9. Nov. 2026 – 15. Nov. 2026" for a week, "November 2026" for a month. */
export function periodLabel(view: CalendarTab, anchorKey: string): string {
  if (view === 'woche') {
    const range = periodRange(view, anchorKey);
    const last = addDays(range.fromKey, 6);
    return `${shortDay.format(utcDate(range.fromKey))} – ${shortDay.format(utcDate(last))}`;
  }
  return monthHeading.format(utcDate(firstOfMonth(anchorKey)));
}

/** "Dienstag, 10. November" for a day key. */
export function formatDayHeading(dayKey: string): string {
  return dayHeading.format(utcDate(dayKey));
}

/** "10.11.2026, 18:00" for an instant, in Berlin. */
export function formatBerlinDateTime(date: Date): string {
  return berlinDateTime.format(date);
}

/** A day key as the value of a date input. */
export function toDateInput(date: Date): string {
  return berlinDateKey(date);
}

function utcDate(dayKey: string): Date {
  const { year, month, day } = parseKey(dayKey);
  return new Date(Date.UTC(year, month - 1, day));
}
