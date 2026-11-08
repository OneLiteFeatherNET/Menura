import { describe, expect, it, vi } from 'vitest';
import {
  fetchExternalPosts,
  postRows,
  syncLabel,
  type ExternalPostItem,
  type ExternalPostPage,
  type PostRow,
} from '../src/api/externalPosts';

const live: ExternalPostItem = {
  id: 'p1',
  platform: 'youtube',
  title: 'Morgenstream',
  format: 'video',
  publishedAt: '2026-11-01T18:00:00Z',
  url: 'https://video.example.test/p1',
  status: 'live',
  removedAt: null,
};

const removed: ExternalPostItem = {
  ...live,
  id: 'p2',
  title: 'Gelöscht',
  url: null,
  status: 'removed',
  removedAt: '2026-11-07T06:00:00Z',
};

const page: ExternalPostPage = {
  channelId: 'c-1',
  channelState: 'connected',
  lastSuccessfulSyncAt: '2026-11-08T06:17:00Z',
  lastFailedSyncAt: null,
  syncFailureReason: null,
  items: [live, removed],
  nextCursor: null,
};

function answer(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('external posts client', () => {
  it('reads the posts of one channel with the session cookie and no token', async () => {
    const fetcher = vi.fn(async () => answer(page));

    await fetchExternalPosts('c-1', null, fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url, 'the request names the channel and the posts collection').toMatch(/\/channels\/c-1\/external-posts$/);
    expect(init.credentials, 'the session cookie travels with the request').toBe('include');
    expect(init.headers, 'no authorization header is ever set by the browser client').not.toHaveProperty('Authorization');
  });

  it('sends the cursor of the next page encoded', async () => {
    const fetcher = vi.fn(async () => answer(page));

    await fetchExternalPosts('c-1', 'abc=|def', fetcher);

    const [url] = fetcher.mock.calls[0] as unknown as [string];
    expect(url, 'the cursor is passed as an encoded query value').toContain('?cursor=abc%3D%7Cdef');
  });

  it('fails with the status when the channel cannot be read', async () => {
    const fetcher = vi.fn(async () => answer({ title: 'Not Found' }, 404));

    await expect(fetchExternalPosts('missing', null, fetcher)).rejects.toThrow('HTTP 404');
  });
});

/** The first row of a one-item list; the test fails at once when there is none. */
function rowOf(items: ExternalPostItem[]): PostRow {
  const row = postRows(items)[0];
  if (!row) {
    throw new Error('the list has no row');
  }
  return row;
}

describe('external posts view', () => {
  it('offers the link of a live post', () => {
    expect(rowOf([live]).link, 'a live post links to the platform').toBe('https://video.example.test/p1');
  });

  it('offers no link for a removed post, but keeps its title and status', () => {
    const row = rowOf([removed]);

    expect(row.link, 'a removed post has no live link').toBeNull();
    expect(row.title, 'the title of the removed post stays visible').toBe('Gelöscht');
    expect(row.status, 'the status says the post was removed').toBe('Entfernt');
  });

  it('says that a channel was never synced completely', () => {
    expect(syncLabel(null)).toBe('Noch nie vollständig synchronisiert.');
  });
});
