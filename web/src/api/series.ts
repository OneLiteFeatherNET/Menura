// The content series (change add-content-series). The API answers with the checked values only; the browser sends its
// session cookie and never reads or stores a token. The functions below are pure where they can be, so the tests run
// without the network.
import { apiBase, type Fetcher } from '../auth/session';

export type Audience = 'players' | 'developers' | 'mixed';

export interface SeriesPlaylist {
  channelId: string;
  playlistId: string;
  playlistName: string;
}

export interface Series {
  id: string;
  name: string;
  badgeText: Record<string, string>;
  colourToken: string;
  badgeToken: string;
  openingFormula: string;
  layoutTemplate: string;
  hashtag: string;
  audience: Audience;
  weekday: number;
  postingSlot: number;
  active: boolean;
  playlists: SeriesPlaylist[];
  createdAt: string;
  updatedAt: string;
}

/** What an editor sends. Colours are token names of the brand kit; a hex value is refused by the server. */
export interface SeriesDraft {
  name: string;
  badgeText: Record<string, string>;
  colourToken: string;
  badgeToken: string;
  openingFormula: string;
  layoutTemplate: string;
  hashtag: string;
  audience: Audience;
  weekday: number;
  postingSlot: number;
  active: boolean;
}

export interface WeekCoverage {
  isoWeek: string;
  weekStart: string;
  shorts: number;
  covered: number;
  noSeries: number;
  percent: number | null;
  flagged: boolean;
}

export interface ChannelCoverage {
  channelId: string;
  displayName: string;
  weeks: WeekCoverage[];
}

export interface SeriesOverview {
  series: Series[];
  warnings: string[];
  coverage: ChannelCoverage[];
}

export interface Episode {
  number: number;
  postId: string;
  title: string;
  format: string;
  publishedAt: string;
  playlistState: string | null;
}

export interface SeriesDetail {
  series: Series;
  episodes: Episode[];
}

/** The warning code the server sends while no active series targets the audience players. */
export const NO_PLAYERS_WARNING = 'no_players_series';

export const AUDIENCE_LABELS: Record<Audience, string> = {
  players: 'Spieler',
  developers: 'Entwickler',
  mixed: 'Gemischt',
};

export const WEEKDAY_LABELS = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];

/** The posting hours of the house rule. */
export const POSTING_SLOTS = [12, 18, 21];

/** The German text of a warning code. An unknown code is shown as it is, never dropped. */
export function warningText(code: string): string {
  if (code === NO_PLAYERS_WARNING) {
    return 'Keine aktive Serie richtet sich an Spieler. Die Shorts sollen Spieler ebenso erreichen wie Entwickler.';
  }
  return code;
}

/** A week in words: the share, the counts, or that the week has no shorts. */
export function coverageLabel(week: WeekCoverage): string {
  if (week.shorts === 0 || week.percent === null) {
    return 'Keine Shorts';
  }
  return `${week.percent} % · ${week.covered} von ${week.shorts} zugeordnet`;
}

/** The text of a failed request: the server's detail when it sent one, else a fixed sentence. */
export async function problemText(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: unknown };
    if (typeof body.detail === 'string' && body.detail.length > 0) {
      return body.detail;
    }
  } catch {
    // The body is not JSON; the fixed sentence is shown instead.
  }
  return fallback;
}

async function readJson<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) {
    throw new Error(await problemText(response, fallback));
  }
  return (await response.json()) as T;
}

/** The overview: every series, the warnings and the weekly coverage. */
export async function fetchSeriesOverview(weeks = 8, fetcher: Fetcher = fetch): Promise<SeriesOverview> {
  const response = await fetcher(`${apiBase()}/series/overview?weeks=${weeks}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  return readJson<SeriesOverview>(response, 'Die Serien konnten nicht geladen werden.');
}

/** One series with its episodes. */
export async function fetchSeriesDetail(id: string, fetcher: Fetcher = fetch): Promise<SeriesDetail> {
  const response = await fetcher(`${apiBase()}/series/${encodeURIComponent(id)}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  return readJson<SeriesDetail>(response, 'Die Serie konnte nicht geladen werden.');
}

/** Stores a new series. A refusal is thrown with the server's reason, so the form can show it next to the fields. */
export async function createSeries(draft: SeriesDraft, fetcher: Fetcher = fetch): Promise<Series> {
  const response = await fetcher(`${apiBase()}/series`, {
    method: 'POST',
    credentials: 'include',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(draft),
  });
  return readJson<Series>(response, 'Die Serie konnte nicht gespeichert werden.');
}

/** Every series, active and inactive, for the calendar filter and the episode planning. */
export async function listSeries(fetcher: Fetcher = fetch): Promise<Series[]> {
  const response = await fetcher(`${apiBase()}/series`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  return readJson<Series[]>(response, 'Die Serien konnten nicht geladen werden.');
}
