import { ApiError, type FetchLike } from '../../api/client';
import type { ProblemDetails } from '../../api/types';
import type { Asset, AssetDetail, AssetFilter, NewAsset, Person, TagsUpdate } from './types';

export type { FetchLike };

const defaultFetch: FetchLike = (input, init) => fetch(input, init);

async function readProblem(response: Response): Promise<ProblemDetails> {
  try {
    return (await response.json()) as ProblemDetails;
  } catch {
    return { status: response.status, title: response.statusText || 'Fehler' };
  }
}

/** The query string of the list filters: set keys only, under the contract names. */
export function assetQuery(filter: AssetFilter = {}): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}

/** Typed client for the pool endpoints. Relative URLs and the session cookie, like the shared client. */
export function createPoolApi(fetcher: FetchLike = defaultFetch) {
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
    people: () => request<Person[]>('GET', '/api/prototype/people'),
    assets: (filter: AssetFilter = {}) => request<Asset[]>('GET', `/api/prototype/assets${assetQuery(filter)}`),
    asset: (id: string) => request<AssetDetail>('GET', `/api/prototype/assets/${encodeURIComponent(id)}`),
    updateTags: (id: string, body: TagsUpdate) =>
      request<Asset>('PUT', `/api/prototype/assets/${encodeURIComponent(id)}/tags`, body),
    createAsset: (body: NewAsset) => request<Asset>('POST', '/api/prototype/assets', body),
  };
}

export type PoolApi = ReturnType<typeof createPoolApi>;

export const poolApi: PoolApi = createPoolApi();
