import type {
  BrandKit,
  Channel,
  Coverage,
  NewSeries,
  Post,
  PostSeriesUpdate,
  ProblemDetails,
  RenderBundle,
  Series,
  SeriesComparison,
  Warning,
  WeeklyAttribution,
} from './types';

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

/** A non-2xx answer from the server, with its Problem Details body. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails;

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/** A German sentence for the user, whatever went wrong. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.problem.title ?? 'Die Anfrage wurde abgelehnt.';
  }
  if (error instanceof TypeError) {
    return 'Der Server ist gerade nicht erreichbar.';
  }
  return 'Unerwarteter Fehler.';
}

const defaultFetch: FetchLike = (input, init) => fetch(input, init);

async function readProblem(response: Response): Promise<ProblemDetails> {
  try {
    return (await response.json()) as ProblemDetails;
  } catch {
    return { status: response.status, title: response.statusText || 'Fehler' };
  }
}

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

/**
 * Typed client for the prototype API. Relative URLs and credentials: 'include' keep the session
 * cookie on the same host as the UI.
 */
export function createApiClient(fetcher: FetchLike = defaultFetch) {
  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const init: RequestInit = { method, credentials: 'include', headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }

    const response = await fetcher(path, init);
    if (!response.ok) {
      throw new ApiError(response.status, await readProblem(response));
    }
    const text = await response.text();
    return (text === '' ? undefined : JSON.parse(text)) as T;
  }

  return {
    brandKit: () => request<BrandKit>('GET', '/api/prototype/brand-kit'),
    channels: () => request<Channel[]>('GET', '/api/prototype/channels'),
    series: () => request<Series[]>('GET', '/api/prototype/series'),
    createSeries: (body: NewSeries) => request<Series>('POST', '/api/prototype/series', body),
    posts: (filter: { seriesId?: string; channelId?: string } = {}) =>
      request<Post[]>(
        'GET',
        `/api/prototype/posts${queryString({ seriesId: filter.seriesId, channelId: filter.channelId })}`,
      ),
    assignPostSeries: (postId: string, body: PostSeriesUpdate) =>
      request<Post>('PUT', `/api/prototype/posts/${encodeURIComponent(postId)}/series`, body),
    coverage: () => request<Coverage[]>('GET', '/api/prototype/coverage'),
    weeklyAttribution: () =>
      request<WeeklyAttribution[]>('GET', '/api/prototype/attribution/weekly'),
    seriesComparison: (week?: string) =>
      request<SeriesComparison[]>(
        'GET',
        `/api/prototype/series-comparison${queryString({ week })}`,
      ),
    warnings: () => request<Warning[]>('GET', '/api/prototype/warnings'),
    renderBundle: () => request<RenderBundle>('GET', '/api/prototype/render-bundle'),
    logout: () => request<void>('POST', '/auth/logout'),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;

export const api: ApiClient = createApiClient();
