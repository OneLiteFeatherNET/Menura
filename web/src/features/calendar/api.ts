import { ApiError, type FetchLike } from '../../api/client';
import type { Channel, Series } from '../../api/types';
import type {
  Capability,
  ClipSummary,
  NewPublication,
  Publication,
  PublicationUpdate,
  SeriesPlan,
  Snapshot,
} from './types';

export interface PublicationFilter {
  from?: string;
  to?: string;
  channelId?: string;
  seriesId?: string;
  status?: string;
}

const defaultFetch: FetchLike = (input, init) => fetch(input, init);

function queryString(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, value);
    }
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}

async function problemOf(response: Response) {
  try {
    return (await response.json()) as { status: number; title?: string; detail?: string };
  } catch {
    return { status: response.status, title: response.statusText || 'Fehler' };
  }
}

/** Typed client of the calendar endpoints. Relative URLs and the session cookie, as in the shared client. */
export function createCalendarApi(fetcher: FetchLike = defaultFetch) {
  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const init: RequestInit = { method, credentials: 'include', headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    const response = await fetcher(path, init);
    if (!response.ok) {
      throw new ApiError(response.status, await problemOf(response));
    }
    const text = await response.text();
    return (text === '' ? undefined : JSON.parse(text)) as T;
  }

  return {
    channels: () => request<Channel[]>('GET', '/api/prototype/channels'),
    series: () => request<Series[]>('GET', '/api/prototype/series'),
    platforms: () => request<Record<string, Capability>>('GET', '/api/prototype/platforms'),
    publications: (filter: PublicationFilter = {}) =>
      request<Publication[]>(
        'GET',
        `/api/prototype/publications${queryString({
          from: filter.from,
          to: filter.to,
          channelId: filter.channelId,
          seriesId: filter.seriesId,
          status: filter.status,
        })}`,
      ),
    createPublications: (body: NewPublication) =>
      request<Publication[]>('POST', '/api/prototype/publications', body),
    updatePublication: (id: string, body: PublicationUpdate) =>
      request<Publication>('PUT', `/api/prototype/publications/${encodeURIComponent(id)}`, body),
    deletePublication: (id: string) =>
      request<void>('DELETE', `/api/prototype/publications/${encodeURIComponent(id)}`),
    planSeries: (seriesId: string, body: SeriesPlan) =>
      request<Publication[]>('POST', `/api/prototype/series/${encodeURIComponent(seriesId)}/plan`, body),
    renderedClips: () => request<ClipSummary[]>('GET', '/api/prototype/assets?stage=rendered'),
    /** The metric snapshots of a published post; an absent post history (404) is an empty list. */
    snapshots: async (postId: string): Promise<Snapshot[]> => {
      try {
        return await request<Snapshot[]>(
          'GET',
          `/api/prototype/analytics/posts/${encodeURIComponent(postId)}/snapshots`,
        );
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return [];
        }
        throw error;
      }
    },
  };
}

export type CalendarApi = ReturnType<typeof createCalendarApi>;

export const calendarApi: CalendarApi = createCalendarApi();
