import type { Audience, JoinSource } from '../api/types';

export const NOT_AVAILABLE = 'nicht verfügbar';

const count = new Intl.NumberFormat('de-DE');
const decimal = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 });
const percent = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const timestamp = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Europe/Berlin',
});

export function formatCount(value: number): string {
  return count.format(value);
}

/** Joins can be fractional when they are estimates: 1.5 reads as "1,5". */
export function formatJoins(value: number): string {
  return decimal.format(value);
}

/** A percentage with one decimal place, or "nicht verfügbar" when the platform gave no value. */
export function formatViewPercent(value: number | null): string {
  if (value === null) {
    return NOT_AVAILABLE;
  }
  return `${percent.format(value)} %`;
}

export function formatPublished(iso: string): string {
  return timestamp.format(new Date(iso));
}

const AUDIENCE_LABELS: Record<Audience, string> = {
  players: 'Spieler',
  developers: 'Entwickler',
  mixed: 'Gemischt',
};

export function audienceLabel(audience: Audience): string {
  return AUDIENCE_LABELS[audience];
}

const WEEKDAY_LABELS: Record<string, string> = {
  MONDAY: 'Montag',
  TUESDAY: 'Dienstag',
  WEDNESDAY: 'Mittwoch',
  THURSDAY: 'Donnerstag',
  FRIDAY: 'Freitag',
  SATURDAY: 'Samstag',
  SUNDAY: 'Sonntag',
};

export function weekdayLabel(weekday: string): string {
  return WEEKDAY_LABELS[weekday] ?? weekday;
}

export const PLATFORM_LABELS: Record<JoinSource, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  twitch: 'Twitch',
  friends: 'Freunde',
  other: 'Sonstiges',
  unanswered: 'keine Angabe',
};
