// The external posts of a channel (change add-external-post-import). The API answers with metadata and the platform link
// only; a removed post has no link, so the view never offers one.
import { apiBase, type Fetcher } from '../auth/session';

export type ChannelState = 'connected' | 'needs_reconnection' | 'disconnected';

export interface ExternalPostItem {
  id: string;
  platform: string;
  title: string;
  format: string;
  publishedAt: string;
  url: string | null;
  status: 'live' | 'removed';
  removedAt: string | null;
}

export interface ExternalPostPage {
  channelId: string;
  channelState: ChannelState;
  lastSuccessfulSyncAt: string | null;
  lastFailedSyncAt: string | null;
  syncFailureReason: string | null;
  items: ExternalPostItem[];
  nextCursor: string | null;
}

/** One table row of the overview. link is null for a removed post. */
export interface PostRow {
  id: string;
  title: string;
  format: string;
  publishedAt: string;
  status: string;
  link: string | null;
}

/** One page of a channel's posts. The browser sends its session cookie; no token is read or stored here. */
export async function fetchExternalPosts(
  channelId: string,
  cursor: string | null = null,
  fetcher: Fetcher = fetch,
): Promise<ExternalPostPage> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  const response = await fetcher(`${apiBase()}/channels/${encodeURIComponent(channelId)}/external-posts${query}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`The posts could not be read (HTTP ${response.status}).`);
  }
  return (await response.json()) as ExternalPostPage;
}

/** The rows of the overview: a removed post keeps its title but no link. */
export function postRows(items: ExternalPostItem[]): PostRow[] {
  return items.map((item) => ({
    id: item.id,
    title: item.title,
    format: item.format,
    publishedAt: item.publishedAt,
    status: statusLabel(item.status),
    link: item.status === 'live' && item.url ? item.url : null,
  }));
}

export function statusLabel(status: 'live' | 'removed'): string {
  return status === 'removed' ? 'Entfernt' : 'Live';
}

export function stateLabel(state: ChannelState): string {
  switch (state) {
    case 'connected':
      return 'Verbunden';
    case 'needs_reconnection':
      return 'Wartet auf Neuverbindung';
    case 'disconnected':
      return 'Getrennt';
  }
}

/** The time of the last complete sync, or a plain statement that there was none. */
export function syncLabel(lastSuccessfulSyncAt: string | null): string {
  if (!lastSuccessfulSyncAt) {
    return 'Noch nie vollständig synchronisiert.';
  }
  return `Letzte vollständige Synchronisierung: ${new Date(lastSuccessfulSyncAt).toLocaleString('de-DE')}`;
}
