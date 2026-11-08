import { ApiError, type FetchLike } from '../../api/client';
import type { ProblemDetails } from '../../api/types';
import type {
  AggregateQuery,
  AggregateResult,
  Heatmap,
  HeatmapQuery,
  Metric,
  PostSnapshot,
} from './types';

const defaultFetch: FetchLike = (input, init) => fetch(input, init);

async function readProblem(response: Response): Promise<ProblemDetails> {
  try {
    return (await response.json()) as ProblemDetails;
  } catch {
    return { status: response.status, title: response.statusText || 'Fehler' } as ProblemDetails;
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
 * Typed reads of the analytics API. An empty channel list is not sent: the server would then answer for every
 * channel, so callers show a hint instead of asking.
 */
export function createAnalyticsApi(fetcher: FetchLike = defaultFetch) {
  async function get<T>(path: string, params: Record<string, string | undefined>): Promise<T> {
    const response = await fetcher(`${path}${queryString(params)}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new ApiError(response.status, await readProblem(response));
    }
    return (await response.json()) as T;
  }

  return {
    metrics: () => get<Metric[]>('/api/prototype/analytics/metrics', {}),
    aggregate: (query: AggregateQuery) =>
      get<AggregateResult>('/api/prototype/analytics/aggregate', {
        metric: query.metric,
        channelIds: query.channelIds?.join(','),
        groupBy: query.groupBy,
        offset: query.offset,
        from: query.from,
        to: query.to,
      }),
    heatmap: (query: HeatmapQuery) =>
      get<Heatmap>('/api/prototype/analytics/heatmap', {
        metric: query.metric,
        channelIds: query.channelIds?.join(','),
        offset: query.offset,
      }),
    snapshots: (postId: string) =>
      get<PostSnapshot[]>(`/api/prototype/analytics/posts/${encodeURIComponent(postId)}/snapshots`, {}),
  };
}

export type AnalyticsApi = ReturnType<typeof createAnalyticsApi>;

export const analyticsApi: AnalyticsApi = createAnalyticsApi();
