import { describe, expect, it, vi } from 'vitest';
import { apiBase } from '../src/auth/session';
import { ApiError } from '../src/api/client';
import { fetchCalendar, feedUrl, issueFeedToken, planPosts, schedulePost } from '../src/api/posts';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('calendar read', () => {
  it('reads the calendar of the view with the session cookie and no token', async () => {
    const fetcher = vi.fn(async () => jsonResponse(200, { timeZone: 'Europe/Berlin', items: [] }));

    await fetchCalendar('view=week&date=2026-11-09', fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${apiBase()}/calendar?view=week&date=2026-11-09`);
    expect(init.credentials, 'the session cookie is sent').toBe('include');
    expect(new Headers(init.headers).has('Authorization'), 'no token is sent').toBe(false);
  });
});

describe('schedule refusal', () => {
  it('keeps the dry-run problems of a 422 answer', async () => {
    const problems = [{ code: 'text_too_long', severity: 'error', message: 'Too long', action: null }];
    const fetcher = vi.fn(async () =>
      jsonResponse(422, {
        type: 'about:blank',
        title: 'Unprocessable Entity',
        status: 422,
        detail: 'The schedule dry run found blocking problems.',
        instance: '/posts/1/schedule',
        problems,
      }),
    );

    const failure = await schedulePost('p1', fetcher).catch((error: unknown) => error);

    expect(failure, 'the refusal is an ApiError').toBeInstanceOf(ApiError);
    expect((failure as ApiError).status).toBe(422);
    expect((failure as ApiError).problems, 'the problems travel with the error').toEqual(problems);
  });

  it('answers an error without problems with an empty list', async () => {
    const fetcher = vi.fn(async () => jsonResponse(403, { title: 'Forbidden', status: 403, detail: 'Role required.' }));

    const failure = (await schedulePost('p1', fetcher).catch((error: unknown) => error)) as ApiError;

    expect(failure.problems).toEqual([]);
  });
});

describe('plan request', () => {
  it('sends one channel entry per channel in the body of the plan', async () => {
    const fetcher = vi.fn(async () => jsonResponse(201, { posts: [], refused: [] }));

    await planPosts({ assetId: 'a1', channels: [{ channelId: 'c1', scheduledAt: '2026-11-08T23:30:00.000Z', title: 'Titel' }] }, fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${apiBase()}/post-plans`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body)).channels).toEqual([
      { channelId: 'c1', scheduledAt: '2026-11-08T23:30:00.000Z', title: 'Titel' },
    ]);
  });
});

describe('calendar feed token', () => {
  it('returns the token shown once by the server', async () => {
    const fetcher = vi.fn(async () => jsonResponse(201, { token: 'abc123.secret' }));

    const issued = await issueFeedToken(fetcher);

    expect(issued.token).toBe('abc123.secret');
  });

  it('builds the feed address from the token without changing it', () => {
    expect(feedUrl('abc123.secret')).toBe(`${apiBase()}/calendar/feed/abc123.secret`);
  });

  it('encodes a token that holds characters outside the path set', () => {
    expect(feedUrl('a b/c')).toBe(`${apiBase()}/calendar/feed/a%20b%2Fc`);
  });
});
