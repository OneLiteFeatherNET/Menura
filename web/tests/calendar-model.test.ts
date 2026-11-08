import { describe, expect, it } from 'vitest';
import { ApiError } from '../src/api/client';
import {
  actionErrorText,
  addDays,
  buildPlan,
  calendarQuery,
  dayLabel,
  groupByDay,
  listGroups,
  monthWeeks,
  periodLabel,
  postActions,
  shiftPeriod,
  weekDays,
  zonedInstant,
  EMPTY_CALENDAR_FILTERS,
  EMPTY_PLAN_FORM,
  type CalendarFilters,
  type PlanForm,
} from '../src/calendar/model';
import { sampleItem } from '../src/calendar/samples';

describe('grouping by the local date of the installation zone', () => {
  it('places a post scheduled at 23:30 UTC on the Monday that the zone shows as 00:30', () => {
    // 2026-11-08T23:30:00Z is 00:30 on Monday 2026-11-09 in Europe/Berlin (UTC+1 after the autumn change).
    const late = sampleItem({ id: 'late', startsAt: '2026-11-08T23:30:00Z', localDate: '2026-11-09', localTime: '00:30' });

    const groups = groupByDay([late]);

    expect(groups.get('2026-11-09')?.map((item) => item.id), 'the post is grouped under the local Monday').toEqual(['late']);
    expect(groups.has('2026-11-08'), 'the UTC day is not used for grouping').toBe(false);
  });

  it('orders the posts of one day by their local time', () => {
    const evening = sampleItem({ id: 'evening', localDate: '2026-11-10', localTime: '18:00' });
    const noon = sampleItem({ id: 'noon', localDate: '2026-11-10', localTime: '12:00' });

    const ids = groupByDay([evening, noon]).get('2026-11-10')?.map((item) => item.id);

    expect(ids, 'the noon post comes first').toEqual(['noon', 'evening']);
  });

  it('lists only the days that have posts, in date order, in the list view', () => {
    const page = {
      firstDay: '2026-11-09',
      lastDay: '2026-11-15',
      items: [
        sampleItem({ id: 'b', localDate: '2026-11-12', localTime: '18:00' }),
        sampleItem({ id: 'a', localDate: '2026-11-10', localTime: '12:00' }),
      ],
    };

    const groups = listGroups(page);

    expect(groups.map((group) => group.day), 'days with posts only, in order').toEqual(['2026-11-10', '2026-11-12']);
    expect(groups[0]?.label).toBe('Dienstag, 10. November 2026');
  });
});

describe('month and week boundaries', () => {
  it('starts the month grid on the Monday before the first day and pads the gap with blanks', () => {
    // 2026-11-01 is a Sunday: the first week holds only that day, on the seventh column.
    const weeks = monthWeeks('2026-11-01', '2026-11-30');

    expect(weeks[0], 'the first week of November 2026').toEqual([null, null, null, null, null, null, '2026-11-01']);
    expect(weeks[1]?.[0], 'the second week starts on Monday 2 November').toBe('2026-11-02');
  });

  it('puts the 9th of November on the Monday column of its week', () => {
    const weeks = monthWeeks('2026-11-01', '2026-11-30');

    expect(weeks[2]?.[0]).toBe('2026-11-09');
  });

  it('ends the month grid on the last day and blanks the rest of the last week', () => {
    const weeks = monthWeeks('2026-11-01', '2026-11-30');

    expect(weeks.at(-1), 'the last week of November 2026 ends on Monday the 30th').toEqual([
      '2026-11-30',
      null,
      null,
      null,
      null,
      null,
      null,
    ]);
  });

  it('needs five rows for February 2026, which starts on a Sunday and has 28 days', () => {
    expect(monthWeeks('2026-02-01', '2026-02-28')).toHaveLength(5);
  });

  it('gives a week view the seven days from Monday to Sunday', () => {
    expect(weekDays('2026-11-11')).toEqual([
      '2026-11-09',
      '2026-11-10',
      '2026-11-11',
      '2026-11-12',
      '2026-11-13',
      '2026-11-14',
      '2026-11-15',
    ]);
  });

  it('crosses the year end when adding days', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('moves a month view to the first day of the previous and next month', () => {
    expect(shiftPeriod('month', '2026-01-31', 1)).toBe('2026-02-01');
    expect(shiftPeriod('month', '2026-03-15', -1)).toBe('2026-02-01');
    expect(shiftPeriod('month', '2026-12-05', 1)).toBe('2027-01-01');
  });

  it('moves a week view by seven days and a list by its length', () => {
    expect(shiftPeriod('week', '2026-11-09', 1)).toBe('2026-11-16');
    expect(shiftPeriod('list', '2026-11-09', -1, 14)).toBe('2026-10-26');
  });
});

describe('period and day labels', () => {
  it('names a day in German with its weekday', () => {
    expect(dayLabel('2026-11-09')).toBe('Montag, 9. November 2026');
  });

  it('names a month view by its month and a week view by its calendar week and dates', () => {
    expect(periodLabel('month', '2026-11-01', '2026-11-30')).toBe('November 2026');
    expect(periodLabel('week', '2026-11-09', '2026-11-15')).toBe('KW 46 · 9.–15. November 2026');
  });
});

describe('query of the calendar read', () => {
  const none: CalendarFilters = EMPTY_CALENDAR_FILTERS;

  it('sends only the view and the date when no filter is chosen', () => {
    expect(calendarQuery({ view: 'month', date: '2026-11-01', filters: none })).toBe('view=month&date=2026-11-01');
  });

  it('sends the chosen filters and leaves out the empty ones', () => {
    const query = calendarQuery({
      view: 'week',
      date: '2026-11-09',
      filters: { channelId: 'c1', seriesId: '', status: 'approved' },
    });

    expect(new URLSearchParams(query).get('channelId')).toBe('c1');
    expect(new URLSearchParams(query).get('status')).toBe('approved');
    expect(new URLSearchParams(query).has('seriesId'), 'an empty series filter is not sent').toBe(false);
  });

  it('sends the number of days only for a list', () => {
    expect(calendarQuery({ view: 'list', date: '2026-11-09', days: 31, filters: none })).toContain('days=31');
    expect(calendarQuery({ view: 'month', date: '2026-11-09', days: 31, filters: none })).not.toContain('days');
  });

  it('leaves the date out when the view starts at today in the zone of the server', () => {
    expect(calendarQuery({ view: 'month', filters: none })).toBe('view=month');
  });
});

describe('planning form', () => {
  const posts: PlanForm = {
    ...EMPTY_PLAN_FORM,
    assetId: 'asset-1',
    channelIds: ['channel-1', 'channel-2'],
    title: '  Kubernetes in 30 Sekunden  ',
    localDate: '2026-11-09',
    localTime: '00:30',
  };

  it('sends the typed Berlin time of the installation zone as a UTC instant on every channel', () => {
    const result = buildPlan(posts, 'Europe/Berlin');

    expect('submit' in result && result.submit.kind === 'posts' ? result.submit.request.channels : []).toEqual([
      { channelId: 'channel-1', title: 'Kubernetes in 30 Sekunden', scheduledAt: '2026-11-08T23:30:00.000Z' },
      { channelId: 'channel-2', title: 'Kubernetes in 30 Sekunden', scheduledAt: '2026-11-08T23:30:00.000Z' },
    ]);
  });

  it('leaves the time out when none is typed', () => {
    const result = buildPlan({ ...posts, localDate: '', localTime: '' }, 'Europe/Berlin');

    expect('submit' in result && result.submit.kind === 'posts' ? result.submit.request.channels[0] : {}).not.toHaveProperty(
      'scheduledAt',
    );
  });

  it('refuses a date without a time', () => {
    expect(buildPlan({ ...posts, localTime: '' }, 'Europe/Berlin')).toEqual({
      error: 'Geben Sie Datum und Uhrzeit gemeinsam ein.',
    });
  });

  it('refuses a post without a channel', () => {
    expect(buildPlan({ ...posts, channelIds: [] }, 'Europe/Berlin')).toEqual({
      error: 'Wählen Sie mindestens einen Kanal aus.',
    });
  });

  it('refuses a post without a title after trimming', () => {
    expect(buildPlan({ ...posts, title: '   ' }, 'Europe/Berlin')).toEqual({ error: 'Geben Sie einen Titel ein.' });
  });

  it('refuses a series without a series or a count outside one to ten', () => {
    const episodes: PlanForm = { ...posts, mode: 'episodes', channelId: 'channel-1', seriesId: 'series-1' };

    expect(buildPlan({ ...episodes, count: 11 }, 'Europe/Berlin')).toEqual({
      error: 'Die Anzahl der Folgen muss zwischen 1 und 10 liegen.',
    });
    expect(buildPlan({ ...episodes, seriesId: '' }, 'Europe/Berlin')).toEqual({ error: 'Wählen Sie eine Serie aus.' });
  });

  it('sends the episodes request with the series it belongs to', () => {
    const result = buildPlan({ ...posts, mode: 'episodes', channelId: 'channel-1', seriesId: 'series-1', count: 3 }, 'Europe/Berlin');

    expect(result).toEqual({
      submit: { kind: 'episodes', seriesId: 'series-1', request: { channelId: 'channel-1', assetId: 'asset-1', count: 3 } },
    });
  });
});

describe('German text of a failed action', () => {
  it('says that a refused schedule was not scheduled, whatever the server detail says', () => {
    const refused = new ApiError(422, 'Unprocessable Entity', 'The schedule dry run found blocking problems.');

    expect(actionErrorText(refused, 'fallback')).toBe('Die Prüfung hat Probleme gefunden. Der Beitrag wurde nicht eingeplant.');
  });

  it('names the role when the server refuses the action for the role', () => {
    expect(actionErrorText(new ApiError(403, 'Forbidden', 'Role required.'), 'fallback')).toBe(
      'Ihre Rolle darf diese Aktion nicht ausführen.',
    );
  });

  it('uses the fallback for an error that is not an answer of the server', () => {
    expect(actionErrorText(new Error('network down'), 'Die Aktion ist fehlgeschlagen.')).toBe('Die Aktion ist fehlgeschlagen.');
  });
});

describe('local times of the planning form', () => {
  it('converts a Berlin winter time to UTC', () => {
    expect(zonedInstant('2026-11-09', '00:30', 'Europe/Berlin')).toBe('2026-11-08T23:30:00.000Z');
  });

  it('converts a Berlin summer time to UTC', () => {
    expect(zonedInstant('2026-07-01', '12:00', 'Europe/Berlin')).toBe('2026-07-01T10:00:00.000Z');
  });
});

describe('actions per role and status', () => {
  it('lets an editor send a draft to review but not approve it', () => {
    const draft = sampleItem({ status: 'draft' });

    expect(postActions(draft, ['EDITOR'])).toMatchObject({ submit: true, approve: false, schedule: false });
  });

  it('lets a publisher approve a post in review and schedule an approved post', () => {
    expect(postActions(sampleItem({ status: 'in_review' }), ['PUBLISHER']).approve).toBe(true);
    expect(postActions(sampleItem({ status: 'approved' }), ['PUBLISHER']).schedule).toBe(true);
  });

  it('lets a publisher schedule a failed post that is to be published again', () => {
    expect(postActions(sampleItem({ status: 'failed' }), ['PUBLISHER']).schedule).toBe(true);
  });

  it('shows an editor no approval and no scheduling, whatever the status', () => {
    const approved = sampleItem({ status: 'approved' });

    expect(postActions(approved, ['EDITOR'])).toMatchObject({ approve: false, schedule: false });
  });

  it('shows a viewer no action at all', () => {
    expect(postActions(sampleItem({ status: 'draft' }), ['VIEWER'])).toEqual({
      submit: false,
      approve: false,
      schedule: false,
      decideRevisions: false,
    });
  });
});
