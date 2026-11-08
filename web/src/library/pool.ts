// The pool of the content library: the query of the listing and the one request that reads it. The browser holds
// only the session cookie, which the request sends with credentials.

import { apiBase, type Fetcher } from '../auth/session';

export type PoolStage = 'raw' | 'cut' | 'rendered';
export type PoolUsage = 'used' | 'never_used';

export interface PoolFilters {
  mine: boolean;
  tag: string;
  stage: PoolStage | '';
  usage: PoolUsage | '';
}

export const EMPTY_FILTERS: PoolFilters = { mine: false, tag: '', stage: '', usage: '' };

/** One item of the pool as the server lists it (see PoolItem in contracts/openapi/menura.yaml). */
export interface PoolItem {
  id: string;
  kind: 'raw' | 'clip';
  stage: PoolStage;
  status: 'active' | 'suggested';
  uploaderId: string;
  title: string | null;
  rawItemId: string | null;
  assetId: string | null;
  startSeconds: number | null;
  endSeconds: number | null;
  usage: PoolUsage | null;
  personTags: string[];
  freeTags: string[];
  createdAt: string;
}

export interface PoolPage {
  items: PoolItem[];
  nextCursor: string | null;
}

/** The query string of the listing. Unused filters are left out, so the server applies only what was chosen. */
export function poolQuery(filters: PoolFilters, cursor?: string | null, limit = 50): string {
  const params = new URLSearchParams();
  if (filters.mine) {
    params.set('mine', 'true');
  }
  if (filters.tag.trim()) {
    params.set('tag', filters.tag.trim());
  }
  if (filters.stage) {
    params.set('stage', filters.stage);
  }
  if (filters.usage) {
    params.set('usage', filters.usage);
  }
  params.set('limit', String(limit));
  if (cursor) {
    params.set('cursor', cursor);
  }
  return params.toString();
}

/** One page of the pool for the filters; the first page has no cursor. */
export async function fetchPool(filters: PoolFilters, cursor: string | null = null, fetcher: Fetcher = fetch): Promise<PoolPage> {
  const response = await fetcher(`${apiBase()}/library/items?${poolQuery(filters, cursor)}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Der Content-Pool konnte nicht geladen werden (HTTP ${response.status}).`);
  }
  return (await response.json()) as PoolPage;
}
