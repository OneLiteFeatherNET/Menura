import { describe, expect, it, vi } from 'vitest';
import { apiBase } from '../src/auth/session';
import { fetchChannelQuota } from '../src/api/channels';
import { setYouTubeDeclaration, updatePostVisibility } from '../src/api/posts';
import { ApiError } from '../src/api/client';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('visibility and declaration requests', () => {
  it('sends the visibility as a patch of the post', async () => {
    const fetcher = vi.fn(async () => jsonResponse(200, { id: 'p1' }));

    await updatePostVisibility('p1', 'public', fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${apiBase()}/posts/p1`);
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(String(init.body))).toEqual({ visibility: 'public' });
  });

  it('sends the made-for-kids answer as the declaration and leaves the Short flag out when it is not given', async () => {
    const fetcher = vi.fn(async () => jsonResponse(200, { id: 'p1' }));

    await setYouTubeDeclaration('p1', { madeForKids: false }, fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${apiBase()}/posts/p1/youtube-declaration`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({ madeForKids: false });
  });
});

describe('channel quota', () => {
  it('reads the remaining quota of the channel', async () => {
    const quota = { unitsPerDay: 10000, unitsRemaining: 9800, uploadsPerDay: 6, uploadsRemaining: 5, resetsAt: '2026-11-09T08:00:00Z' };
    const fetcher = vi.fn(async () => jsonResponse(200, quota));

    expect(await fetchChannelQuota('c1', fetcher)).toEqual(quota);
    expect((fetcher.mock.calls[0] as unknown as [string])[0]).toBe(`${apiBase()}/channels/c1/quota`);
  });

  it('reports no quota for a platform without a daily quota', async () => {
    const fetcher = vi.fn(async () => jsonResponse(404, { title: 'Not Found', status: 404, detail: 'No quota.' }));

    expect(await fetchChannelQuota('c1', fetcher)).toBeNull();
  });

  it('keeps any other failure visible', async () => {
    const fetcher = vi.fn(async () => jsonResponse(500, { title: 'Internal', status: 500, detail: 'Boom.' }));

    await expect(fetchChannelQuota('c1', fetcher)).rejects.toBeInstanceOf(ApiError);
  });
});
