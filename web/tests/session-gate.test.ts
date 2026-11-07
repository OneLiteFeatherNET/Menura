import { describe, expect, it } from 'vitest';
import { canEditSeries, loadSession } from '../src/auth/session';

interface RecordedCall {
  url: string;
  init: RequestInit | undefined;
}

// A fetch double that answers every call with the given response and records the calls.
function mockFetch(answer: () => Response): { fetcher: (input: string, init?: RequestInit) => Promise<Response>; calls: RecordedCall[] } {
  const calls: RecordedCall[] = [];
  const fetcher = (input: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url: input, init });
    return Promise.resolve(answer());
  };
  return { fetcher, calls };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('session gate', () => {
  it('reports anonymous when the server answers 401', async () => {
    const { fetcher } = mockFetch(() => new Response(null, { status: 401 }));

    expect(await loadSession(fetcher)).toEqual({ status: 'anonymous' });
  });

  it('reports forbidden when the server answers 403', async () => {
    const { fetcher } = mockFetch(() => new Response(null, { status: 403 }));

    expect(await loadSession(fetcher)).toEqual({ status: 'forbidden' });
  });

  it('reports forbidden when the signed-in user has no roles', async () => {
    const { fetcher } = mockFetch(() => json(200, { subject: 'anna', roles: [] }));

    expect(await loadSession(fetcher)).toEqual({ status: 'forbidden' });
  });

  it('returns the session when the server answers 200 with roles', async () => {
    const { fetcher } = mockFetch(() => json(200, { subject: 'anna', roles: ['VIEWER', 'EDITOR'] }));

    expect(await loadSession(fetcher)).toEqual({
      status: 'signed-in',
      session: { subject: 'anna', roles: ['VIEWER', 'EDITOR'] },
    });
  });

  it('asks for the session with a relative URL and the cookie included', async () => {
    const { fetcher, calls } = mockFetch(() => json(200, { subject: 'anna', roles: ['VIEWER'] }));

    await loadSession(fetcher);

    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe('/auth/session');
    expect(calls[0]?.init?.credentials).toBe('include');
  });

  it('reports an error for other server failures instead of signing the user out', async () => {
    const { fetcher } = mockFetch(() => new Response(null, { status: 500 }));

    const state = await loadSession(fetcher);

    expect(state.status).toBe('error');
  });

  it('reports an error when the server cannot be reached', async () => {
    const fetcher = () => Promise.reject(new TypeError('Failed to fetch'));

    const state = await loadSession(fetcher);

    expect(state.status).toBe('error');
  });
});

describe('edit permission', () => {
  it('allows editors and admins only', () => {
    expect(canEditSeries(['EDITOR'])).toBe(true);
    expect(canEditSeries(['ADMIN'])).toBe(true);
    expect(canEditSeries(['VIEWER'])).toBe(false);
    expect(canEditSeries([])).toBe(false);
  });
});
