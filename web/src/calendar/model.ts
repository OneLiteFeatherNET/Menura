// The content calendar (change add-content-distribution): the view logic. Pure functions only, so the tests run without
// the network. Times are never converted here: the server gives every item its local date and time in the installation
// zone, and the calendar groups and shows those values. The one conversion is the reverse one, of a time typed into the
// planning form, which zonedInstant performs with the zone name the server returned.
import { ApiError } from '../api/client';
import type { ChannelState } from '../api/channels';
import type {
  CalendarItem,
  CalendarPage,
  DryRunProblem,
  EpisodePlanRequest,
  PostPlanRequest,
  PostAttempt,
  PostRevision,
  PostStatus,
  PostStep,
  PostVisibility,
} from '../api/posts';

export type CalendarView = 'month' | 'week' | 'list';

export interface CalendarFilters {
  channelId: string;
  seriesId: string;
  status: PostStatus | '';
}

export const EMPTY_CALENDAR_FILTERS: CalendarFilters = { channelId: '', seriesId: '', status: '' };

/** The days a list covers when the view does not say otherwise (the server default). */
export const LIST_DAYS = 31;

export const VIEW_LABELS: Record<CalendarView, string> = { month: 'Monat', week: 'Woche', list: 'Liste' };

export const STATUS_LABELS: Record<PostStatus, string> = {
  draft: 'Entwurf',
  in_review: 'In Prüfung',
  approved: 'Freigegeben',
  scheduled: 'Geplant',
  published: 'Veröffentlicht',
  failed: 'Fehlgeschlagen',
};

const MONTHS = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
];
const WEEKDAYS = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];

/** A calendar day as YYYY-MM-DD, read and written as a UTC calendar date: no zone is involved. */
function toDate(day: string): Date {
  return new Date(Date.UTC(Number(day.slice(0, 4)), Number(day.slice(5, 7)) - 1, Number(day.slice(8, 10))));
}

function toDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** The day that lies the given number of days from a day (negative counts back). */
export function addDays(day: string, count: number): string {
  const date = toDate(day);
  date.setUTCDate(date.getUTCDate() + count);
  return toDay(date);
}

/** Monday is 0 and Sunday is 6. */
export function weekdayIndex(day: string): number {
  return (toDate(day).getUTCDay() + 6) % 7;
}

/** The Monday on or before the day. */
export function mondayOf(day: string): string {
  return addDays(day, -weekdayIndex(day));
}

/** The rows of a month grid, Monday first. Days outside the month are blank (null). */
export function monthWeeks(firstDay: string, lastDay: string): (string | null)[][] {
  const days: (string | null)[] = [];
  const leading = weekdayIndex(firstDay);
  for (let index = 0; index < leading; index += 1) {
    days.push(null);
  }
  const count = Number(lastDay.slice(8, 10));
  for (let date = 1; date <= count; date += 1) {
    days.push(addDays(firstDay, date - 1));
  }
  while (days.length % 7 !== 0) {
    days.push(null);
  }
  const rows: (string | null)[][] = [];
  for (let start = 0; start < days.length; start += 7) {
    rows.push(days.slice(start, start + 7));
  }
  return rows;
}

/** The seven days of a week view, Monday to Sunday, of the week that holds the day. */
export function weekDays(day: string): string[] {
  const monday = mondayOf(day);
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
}

/**
 * The anchor day of the next or previous period. A month moves to the first of the month, so the anchor stays inside
 * its month; a week moves seven days; a list moves by its length.
 */
export function shiftPeriod(view: CalendarView, anchor: string, step: -1 | 1, listDays = LIST_DAYS): string {
  if (view === 'month') {
    const year = Number(anchor.slice(0, 4));
    const month = Number(anchor.slice(5, 7)) - 1 + step;
    return toDay(new Date(Date.UTC(year, month, 1)));
  }
  if (view === 'week') {
    return addDays(anchor, 7 * step);
  }
  return addDays(anchor, listDays * step);
}

/** The German weekday, date and month of a day, for example 'Montag, 9. November 2026'. */
export function dayLabel(day: string): string {
  const date = toDate(day);
  const weekday = WEEKDAYS[date.getUTCDay()];
  return `${weekday}, ${date.getUTCDate()}. ${MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/** The title of a period: the month, or the calendar week with its dates. */
export function periodLabel(view: CalendarView, firstDay: string, lastDay: string): string {
  if (view === 'month') {
    const date = toDate(firstDay);
    return `${MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
  }
  if (view === 'week') {
    const monday = toDate(firstDay);
    const sunday = toDate(lastDay);
    return `KW ${isoWeekNumber(firstDay)} · ${monday.getUTCDate()}.–${sunday.getUTCDate()}. ${MONTHS[sunday.getUTCMonth()]} ${sunday.getUTCFullYear()}`;
  }
  return `${dayLabel(firstDay)} bis ${dayLabel(lastDay)}`;
}

/** The ISO week number of a day (the week that holds its Thursday). */
export function isoWeekNumber(day: string): number {
  const thursday = addDays(day, 3 - weekdayIndex(day));
  const year = Number(thursday.slice(0, 4));
  const firstThursday = addDays(`${year}-01-04`, 3 - weekdayIndex(`${year}-01-04`));
  return Math.round((toDate(thursday).getTime() - toDate(firstThursday).getTime()) / (7 * 86_400_000)) + 1;
}

/** Posts grouped by their local day, each day ordered by the local time, then by identifier. */
export function groupByDay(items: readonly CalendarItem[]): Map<string, CalendarItem[]> {
  const groups = new Map<string, CalendarItem[]>();
  for (const item of items) {
    const day = groups.get(item.localDate) ?? [];
    day.push(item);
    groups.set(item.localDate, day);
  }
  for (const day of groups.values()) {
    day.sort((a, b) => a.localTime.localeCompare(b.localTime) || a.id.localeCompare(b.id));
  }
  return groups;
}

export interface DayGroup {
  day: string;
  label: string;
  items: CalendarItem[];
}

/** The days of a list view that hold posts, in date order. */
export function listGroups(page: Pick<CalendarPage, 'items'>): DayGroup[] {
  return [...groupByDay(page.items).entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([day, items]) => ({ day, label: dayLabel(day), items }));
}

export interface CalendarQueryInput {
  view: CalendarView;
  date?: string;
  days?: number;
  filters: CalendarFilters;
}

/** The query string of the calendar read. Empty filters and an absent date are left out. */
export function calendarQuery(input: CalendarQueryInput): string {
  const params = new URLSearchParams({ view: input.view });
  if (input.date) {
    params.set('date', input.date);
  }
  if (input.view === 'list' && input.days !== undefined) {
    params.set('days', String(input.days));
  }
  if (input.filters.channelId) {
    params.set('channelId', input.filters.channelId);
  }
  if (input.filters.seriesId) {
    params.set('seriesId', input.filters.seriesId);
  }
  if (input.filters.status) {
    params.set('status', input.filters.status);
  }
  return params.toString();
}

export interface PostActions {
  submit: boolean;
  approve: boolean;
  schedule: boolean;
  decideRevisions: boolean;
}

/** The planning form: one finished asset on the chosen channels, or the next episodes of a series on one channel. */
export interface PlanForm {
  mode: 'posts' | 'episodes';
  assetId: string;
  channelIds: string[];
  channelId: string;
  seriesId: string;
  title: string;
  localDate: string;
  localTime: string;
  count: number;
}

export const EMPTY_PLAN_FORM: PlanForm = {
  mode: 'posts',
  assetId: '',
  channelIds: [],
  channelId: '',
  seriesId: '',
  title: '',
  localDate: '',
  localTime: '',
  count: 1,
};

/** What the page sends for the form: the request of the plan, and for episodes the series it belongs to. */
export type PlanSubmit =
  | { kind: 'posts'; request: PostPlanRequest }
  | { kind: 'episodes'; seriesId: string; request: EpisodePlanRequest };

/**
 * The request of a planning form, or the German reason it cannot be sent. A time is optional: without one the post is
 * planned without a time. A typed time is read in the installation zone and sent as a UTC instant.
 */
export function buildPlan(form: PlanForm, timeZone: string): { submit: PlanSubmit } | { error: string } {
  if (!form.assetId) {
    return { error: 'Wählen Sie einen fertigen Clip aus dem Pool.' };
  }
  if (form.mode === 'episodes') {
    if (!form.seriesId) return { error: 'Wählen Sie eine Serie aus.' };
    if (!form.channelId) return { error: 'Wählen Sie einen Kanal aus.' };
    if (!Number.isInteger(form.count) || form.count < 1 || form.count > 10) {
      return { error: 'Die Anzahl der Folgen muss zwischen 1 und 10 liegen.' };
    }
    return {
      submit: {
        kind: 'episodes',
        seriesId: form.seriesId,
        request: { channelId: form.channelId, assetId: form.assetId, count: form.count },
      },
    };
  }
  if (form.channelIds.length === 0) {
    return { error: 'Wählen Sie mindestens einen Kanal aus.' };
  }
  const title = form.title.trim();
  if (!title) {
    return { error: 'Geben Sie einen Titel ein.' };
  }
  if (Boolean(form.localDate) !== Boolean(form.localTime)) {
    return { error: 'Geben Sie Datum und Uhrzeit gemeinsam ein.' };
  }
  const scheduledAt = form.localDate ? zonedInstant(form.localDate, form.localTime, timeZone) : undefined;
  return {
    submit: {
      kind: 'posts',
      request: {
        assetId: form.assetId,
        channels: form.channelIds.map((channelId) => ({
          channelId,
          title,
          ...(scheduledAt ? { scheduledAt } : {}),
        })),
      },
    },
  };
}

/** A schedule the dry run refused: the server's message and the problems it listed (HTTP 422). */
export interface ScheduleRefusal {
  message: string;
  problems: DryRunProblem[];
}

/** The actions a person may take on a post, by role and status. The server checks the same rules on every request. */
export function postActions(post: Pick<CalendarItem, 'status'>, roles: readonly string[]): PostActions {
  const editor = roles.includes('EDITOR') || roles.includes('PUBLISHER');
  const publisher = roles.includes('PUBLISHER');
  return {
    submit: editor && post.status === 'draft',
    approve: publisher && post.status === 'in_review',
    schedule: publisher && (post.status === 'approved' || post.status === 'failed'),
    decideRevisions: publisher,
  };
}

/** True when a person with these roles may plan posts (the server refuses the others). */
export function canPlan(roles: readonly string[]): boolean {
  return roles.includes('EDITOR') || roles.includes('PUBLISHER');
}

/**
 * The UTC instant of a local date and time in a zone, as an ISO string. The offset of the zone is read from the platform
 * formatter at the instant, and read once more at the corrected instant, so a time next to a daylight-saving change
 * resolves to the offset that is in force then.
 */
export function zonedInstant(localDate: string, localTime: string, timeZone: string): string {
  const wall = Date.parse(`${localDate}T${localTime}:00Z`);
  const first = wall - zoneOffsetMillis(wall, timeZone);
  const second = wall - zoneOffsetMillis(first, timeZone);
  return new Date(second).toISOString();
}

function zoneOffsetMillis(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(instant));
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
  const asUtc = Date.UTC(value('year'), value('month') - 1, value('day'), value('hour'), value('minute'), value('second'));
  return asUtc - Math.floor(instant / 1000) * 1000;
}

export const CHANNEL_STATE_LABELS: Record<ChannelState, string> = {
  connected: 'Verbunden',
  needs_reconnection: 'Neu verbinden',
  disconnected: 'Getrennt',
};

export const STEP_LABELS: Record<PostStep['step'], string> = {
  upload: 'Hochladen',
  schedule: 'Terminierung',
  metadata: 'Metadaten',
  publish: 'Veröffentlichung',
};

export const STEP_STATE_LABELS: Record<PostStep['state'], string> = {
  pending: 'Ausstehend',
  running: 'Läuft',
  done: 'Erledigt',
  failed: 'Fehlgeschlagen',
};

export const OUTCOME_LABELS: Record<NonNullable<PostAttempt['outcome']>, string> = {
  handed_over: 'An die Plattform übergeben',
  published: 'Veröffentlicht',
  retry_scheduled: 'Wiederholung geplant',
  failed: 'Fehlgeschlagen',
  unknown: 'Ergebnis unbekannt',
};

export const REVISION_STATUS_LABELS: Record<PostRevision['status'], string> = {
  pending: 'Offen',
  accepted: 'Angenommen',
  rejected: 'Abgelehnt',
};

export const VISIBILITY_LABELS: Record<PostVisibility, string> = {
  private: 'Privat',
  unlisted: 'Nicht gelistet',
  public: 'Öffentlich',
};

/** The German text of a failed calendar action. The status decides the text; the server's English detail is not shown. */
export function actionErrorText(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) {
    return fallback;
  }
  switch (error.status) {
    case 401:
      return 'Ihre Anmeldung ist abgelaufen. Bitte melden Sie sich erneut an.';
    case 403:
      return 'Ihre Rolle darf diese Aktion nicht ausführen.';
    case 404:
      return 'Der Beitrag wurde nicht gefunden.';
    case 409:
      return 'Die Aktion ist im aktuellen Status des Beitrags nicht möglich.';
    case 422:
      return 'Die Prüfung hat Probleme gefunden. Der Beitrag wurde nicht eingeplant.';
    default:
      return fallback;
  }
}
