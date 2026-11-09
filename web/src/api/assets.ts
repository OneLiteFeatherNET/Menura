// The finished assets of the library (change add-asset-upload): a verified video or image, never raw material. The
// listing carries metadata only; the calendar plans a post on an asset by its identifier.
import type { Fetcher } from '../auth/session';
import { apiRequest } from './client';

export type AssetKind = 'video' | 'image' | 'raw_material';

export interface AssetView {
  id: string;
  kind: AssetKind;
  format: string;
  durationSeconds: number | null;
  width: number;
  height: number;
  sizeBytes: number;
  checksum: string;
  uploaderId: string;
  streamTitle: string | null;
  streamDate: string | null;
  createdAt: string;
}

interface AssetPageBody {
  items?: AssetView[];
  nextCursor?: string | null;
}

const PAGE_SIZE = 100;

/** The finished assets, following the cursor. The loop stops when the server repeats a cursor, so it cannot hang. */
export async function listFinishedAssets(fetcher?: Fetcher): Promise<AssetView[]> {
  const assets: AssetView[] = [];
  const seen = new Set<string>();
  let cursor: string | null = null;
  do {
    const query: string = `?limit=${PAGE_SIZE}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const page: AssetPageBody | undefined = await apiRequest<AssetPageBody>(`/assets${query}`, {}, fetcher);
    if (!page) break;
    assets.push(...(page.items ?? []).filter((asset) => asset.kind !== 'raw_material'));
    cursor = page.nextCursor ?? null;
    if (cursor !== null) {
      if (seen.has(cursor)) break;
      seen.add(cursor);
    }
  } while (cursor !== null);
  return assets;
}
