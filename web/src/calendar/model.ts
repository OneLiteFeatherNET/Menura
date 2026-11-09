// The content calendar (change add-content-distribution): the view logic. Pure functions only, so the tests run without
// the network. Times are never converted here: the server gives every item its local date and time in the installation
// zone, and the calendar groups and shows those values. The one conversion is the reverse one, of a time typed into the
// planning form, which zonedInstant performs with the zone name the server returned.
import { ApiError } from '../api/client';
import type { ChannelState } from '../api/channels';
import type {
  CalendarItem,
  CalendarPage,
  ChannelQuota,
  DryRunProblem,
  Post,
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

/** The German texts of the dry-run problem codes. A code not listed here is shown with the server's own message. */
export const PROBLEM_TEXTS: Record<string, string> = {
  asset_missing: 'Dem Beitrag fehlt der fertige Clip.',
  format_not_allowed: 'Die Plattform nimmt diese Art von Datei für diesen Kanal nicht an.',
  title_too_long: 'Der Titel ist für diesen Kanal zu lang.',
  description_too_long: 'Der Text ist für diesen Kanal zu lang.',
  too_many_hashtags: 'Der Kanal nimmt nicht so viele Hashtags.',
  connection_unusable: 'Der Kanal muss neu verbunden werden, bevor der Beitrag hinausgeht.',
  audit_restricted: 'Die Plattform-App ist nicht geprüft, daher ist die öffentliche Sichtbarkeit nicht erlaubt.',
  missing_time: 'Der Beitrag hat noch keinen Termin.',
  collision: 'Ein anderer Beitrag desselben Kanals ist zur selben Zeit geplant.',
  visibility_undeclared: 'Wählen Sie die Sichtbarkeit des Beitrags, bevor er eingeplant wird.',
  made_for_kids_missing: 'Beantworten Sie, ob das Video für Kinder bestimmt ist, bevor es eingeplant wird.',
  short_not_vertical: 'Ein Short muss hochkant oder quadratisch sein.',
  short_too_long: 'Ein Short darf höchstens drei Minuten lang sein.',
  short_unverified: 'Die Größe oder Dauer des Clips ist nicht erfasst, daher lässt sich nicht prüfen, ob er ein Short ist.',
  playlist_not_owned:
    'Die Playlist dieses Beitrags gehört nicht zu diesem Kanal und ist daher nicht verfügbar. Wählen Sie in der Serie eine Playlist dieses Kanals.',
  playlist_unverified:
    'Die Playlist dieses Beitrags konnte nicht gegen den Kanal geprüft werden, daher wird er nicht eingeplant. Versuchen Sie es später erneut.',
  thumbnail_not_allowed:
    'Die Plattform erlaubt für diesen Kanal kein eigenes Vorschaubild. Das Vorschaubild wird daher nicht gesetzt.',
};

/** The German text of one dry-run problem; a code without a German text keeps the server's message. */
export function problemText(problem: DryRunProblem): string {
  return PROBLEM_TEXTS[problem.code] ?? problem.message;
}

/** The made-for-kids and visibility fields of the declaration form, as the person has filled them. */
export interface DeclarationForm {
  visibility: string;
  madeForKids: boolean | null;
  plannedShort: boolean;
}

/** The German reason the declaration cannot be saved yet, or null when it is complete. */
export function declarationError(form: DeclarationForm): string | null {
  if (!form.visibility) {
    return 'Wählen Sie die Sichtbarkeit.';
  }
  if (form.madeForKids === null) {
    return 'Beantworten Sie, ob das Video für Kinder bestimmt ist.';
  }
  return null;
}

/** Whether a post goes to YouTube, the platform with the declarations of this change. */
export function isYouTube(platform: string): boolean {
  return platform === 'youtube';
}

/** The visibility declaration can be changed while the post is a draft or in review, as on the server. */
export function canDeclare(status: PostStatus, roles: readonly string[]): boolean {
  return (roles.includes('EDITOR') || roles.includes('PUBLISHER')) && (status === 'draft' || status === 'in_review');
}

/** The German note of a platform's applied visibility that differs from the requested one, or null when they agree. */
export function visibilityMismatchText(post: Pick<Post, 'visibility' | 'appliedVisibility'>): string | null {
  if (!post.appliedVisibility || post.appliedVisibility === post.visibility) {
    return null;
  }
  return `Angewendet: ${VISIBILITY_LABELS[post.appliedVisibility]}, gewählt: ${VISIBILITY_LABELS[post.visibility]}.`;
}

/** The remaining quota in words: units and uploads of the day that are still left. */
export function quotaLine(quota: Pick<ChannelQuota, 'unitsPerDay' | 'unitsRemaining' | 'uploadsPerDay' | 'uploadsRemaining'>): string {
  return `${quota.unitsRemaining} von ${quota.unitsPerDay} Einheiten · ${quota.uploadsRemaining} von ${quota.uploadsPerDay} Uploads frei`;
}

/** The moment the quota starts over, in the installation zone the API names (for example 09.11.2026, 09:00). */
export function resetLabel(resetsAt: string, timeZone: string): string {
  return new Intl.DateTimeFormat('de-DE', {
    timeZone,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(resetsAt));
}

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
