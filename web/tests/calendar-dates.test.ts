import { describe, expect, it } from 'vitest';
import {
  addDays,
  berlinDateKey,
  berlinHour,
  berlinTime,
  monthGrid,
  periodRange,
  rangeToInstants,
  shiftAnchor,
  weekDays,
  weekdayIndex,
  zonedToDate,
} from '../src/features/calendar/dates';

describe('Berlin time of an instant', () => {
  it('reads the hour in Berlin, not in UTC, in winter time', () => {
    expect(berlinHour(new Date('2026-11-10T16:30:00Z')), 'Berlin hour').toBe(17);
  });

  it('keeps 18:00 Berlin on both sides of the end of summer time', () => {
    expect(berlinTime(new Date('2026-10-20T16:00:00Z')), 'before the change').toBe('18:00');
    expect(berlinTime(new Date('2026-10-27T17:00:00Z')), 'after the change').toBe('18:00');
  });

  it('decides the day by the Berlin date, not by the UTC date', () => {
    expect(berlinDateKey(new Date('2026-11-09T23:30:00Z')), 'Berlin date').toBe('2026-11-10');
  });
});

describe('Berlin wall time to instant', () => {
  it('converts 18:00 Berlin to 16:00 UTC in summer time', () => {
    expect(zonedToDate('2026-10-20', '18:00').toISOString()).toBe('2026-10-20T16:00:00.000Z');
  });

  it('converts 18:00 Berlin to 17:00 UTC in winter time', () => {
    expect(zonedToDate('2026-11-10', '18:00').toISOString()).toBe('2026-11-10T17:00:00.000Z');
  });
});

describe('calendar grids', () => {
  it('starts the week on a Monday', () => {
    expect(weekdayIndex('2026-11-09'), 'Monday').toBe(0);
    expect(weekdayIndex('2026-11-15'), 'Sunday').toBe(6);
  });

  it('runs the week of an anchor from Monday to Sunday', () => {
    const days = weekDays('2026-11-11');

    expect(days[0], 'first day').toBe('2026-11-09');
    expect(days[6], 'last day').toBe('2026-11-15');
    expect(days).toHaveLength(7);
  });

  it('builds a six-week month grid that starts on a Monday', () => {
    const grid = monthGrid('2026-11-08');

    expect(grid, 'cells').toHaveLength(42);
    expect(grid[0], 'first cell').toBe('2026-10-26');
    expect(weekdayIndex(grid[0] ?? ''), 'first cell is a Monday').toBe(0);
  });

  it('adds days across a month boundary', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
  });
});

describe('periods', () => {
  it('spans the week in Berlin midnights, end exclusive', () => {
    const range = periodRange('woche', '2026-11-11');

    expect(rangeToInstants(range)).toEqual({
      from: '2026-11-08T23:00:00.000Z',
      to: '2026-11-15T23:00:00.000Z',
    });
  });

  it('moves the anchor by one month in the month view', () => {
    expect(shiftAnchor('monat', '2026-11-08', 1)).toBe('2026-12-08');
  });
});
