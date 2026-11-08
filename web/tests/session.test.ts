import { describe, expect, it, vi } from 'vitest';
import { fetchSession, roleLabel, signOut } from '../src/auth/session';

// The fetcher is injected, so these tests never reach the network.
function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('fetchSession', () => {
  it('returns the subject and roles of a signed-in person and sends the session cookie', async () => {
    const fetcher = vi.fn(async () => jsonResponse(200, { subject: 'e2e-editor-1', roles: ['EDITOR'] }));

    const session = await fetchSession(fetcher);

    expect(session).toEqual({ subject: 'e2e-editor-1', roles: ['EDITOR'] });
    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/auth\/session$/);
    expect(init.credentials, 'the browser sends its session cookie to the API host').toBe('include');
  });

  it('returns null for an anonymous visitor instead of failing', async () => {
    const fetcher = async () => jsonResponse(401, { status: 401 });

    expect(await fetchSession(fetcher)).toBeNull();
  });

  it('fails for any other status so the shell can offer a retry', async () => {
    const fetcher = async () => jsonResponse(503, { status: 503 });

    await expect(fetchSession(fetcher)).rejects.toThrow(/503/);
  });
});

describe('signOut', () => {
  it('ends the session on the API with a POST that carries the cookie', async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 }));

    await signOut(fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/auth\/logout$/);
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
  });
});

describe('roleLabel', () => {
  it('names every role of the server in German', () => {
    expect(roleLabel('VIEWER')).toBe('Betrachter');
    expect(roleLabel('EDITOR')).toBe('Redaktion');
    expect(roleLabel('PUBLISHER')).toBe('Veröffentlichung');
    expect(roleLabel('OPERATOR')).toBe('Betrieb');
    expect(roleLabel('ADMIN')).toBe('Administration');
  });
});
