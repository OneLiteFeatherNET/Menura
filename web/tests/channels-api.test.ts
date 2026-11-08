import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiRequest } from '../src/api/client';
import {
  consentUrl,
  disconnectChannel,
  listChannels,
  listPlatforms,
  recordAuditStatus,
  startConnection,
  startReconnection,
} from '../src/api/channels';

// The fetcher is injected, so no test reaches the network. Each call is recorded and checked for its method, path,
// credentials and body.
function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function fetcherReplying(...responses: Response[]) {
  return vi.fn(async () => responses.shift() ?? jsonResponse(500, { title: 'no reply' }));
}

function callOf(fetcher: ReturnType<typeof fetcherReplying>, index = 0) {
  const [url, init] = fetcher.mock.calls[index] as unknown as [string, RequestInit];
  return { url, init };
}

describe('apiRequest', () => {
  it('sends the session cookie and asks for JSON', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, []));

    await apiRequest('/platforms', {}, fetcher);

    const { url, init } = callOf(fetcher);
    expect(url).toMatch(/\/platforms$/);
    expect(init.credentials, 'the browser sends its session cookie').toBe('include');
    expect(init.method).toBe('GET');
    expect((init.headers as Record<string, string>).Accept).toBe('application/json');
  });

  it('sends a JSON body with its content type', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, {}));

    await apiRequest('/channels/connections', { method: 'POST', body: { platform: 'youtube' } }, fetcher);

    const { init } = callOf(fetcher);
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual({ platform: 'youtube' });
  });

  it('returns undefined for an answer without a body', async () => {
    const fetcher = fetcherReplying(new Response(null, { status: 204 }));

    expect(await apiRequest('/anything', { method: 'DELETE' }, fetcher)).toBeUndefined();
  });

  it('throws an ApiError that carries the status and the problem detail of a failed request', async () => {
    const fetcher = fetcherReplying(
      jsonResponse(403, { type: 'about:blank', title: 'Forbidden', status: 403, detail: 'Role required.' }),
    );

    const error = await apiRequest('/channels', {}, fetcher).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
    expect((error as ApiError).title).toBe('Forbidden');
    expect((error as ApiError).message).toBe('Role required.');
  });

  it('keeps the status text for a failure without a problem body', async () => {
    const fetcher = fetcherReplying(new Response('<html>gateway</html>', { status: 502, statusText: 'Bad Gateway' }));

    const error = (await apiRequest('/channels', {}, fetcher).catch((caught: unknown) => caught)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.title).toBe('Bad Gateway');
  });
});

describe('listPlatforms', () => {
  it('returns the platforms of the build', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, [{ platform: 'youtube', displayName: 'YouTube' }]));

    const platforms = await listPlatforms(fetcher);

    expect(platforms).toMatchObject([{ platform: 'youtube', displayName: 'YouTube' }]);
    expect(platforms[0]?.capabilities, 'a platform that declares no capability offers none').toEqual({
      publishing: false,
      nativeScheduling: false,
      listing: false,
      metrics: false,
      revocation: false,
    });
    expect(callOf(fetcher).url).toMatch(/\/platforms$/);
  });
});

describe('recordAuditStatus', () => {
  it('sends the status and its source with a PUT to the platform path', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, { status: 'unaudited', displayText: '', restrictions: [] }));

    await recordAuditStatus('youtube', { status: 'unaudited', source: 'Prüfschreiben' }, fetcher);

    const { url, init } = callOf(fetcher);
    expect(url).toMatch(/\/platforms\/youtube\/audit-status$/);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({ status: 'unaudited', source: 'Prüfschreiben' });
  });

  it('leaves the source out when it is empty', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, { status: 'unknown', displayText: '', restrictions: [] }));

    await recordAuditStatus('youtube', { status: 'unknown' }, fetcher);

    expect(JSON.parse(callOf(fetcher).init.body as string)).toEqual({ status: 'unknown' });
  });

  it('fails when the server answers without the recorded status', async () => {
    const fetcher = fetcherReplying(new Response(null, { status: 204 }));

    await expect(recordAuditStatus('youtube', { status: 'audited' }, fetcher)).rejects.toThrow(/without a body/);
  });
});

describe('listChannels', () => {
  // The server leaves out null and empty members of a response, so an empty page arrives as an empty object.
  it('reads a page without items as an empty list', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, {}));

    expect(await listChannels(fetcher)).toEqual([]);
  });

  it('fills the null and empty members the server left out of a channel', async () => {
    const fetcher = fetcherReplying(
      jsonResponse(200, {
        items: [
          {
            id: 'a',
            platform: 'youtube',
            displayName: 'Kanal',
            state: 'connected',
            quota: { warning: false },
            audit: { status: 'unaudited', displayText: 'unaudited' },
          },
        ],
      }),
    );

    const [channel] = await listChannels(fetcher);

    expect(channel?.quota).toEqual({ used: null, limit: null, resetAt: null, warning: false, delay: null });
    expect(channel?.grantedScopes).toEqual([]);
    expect(channel?.stateReason).toBeNull();
    expect(channel?.audit.restrictions).toEqual([]);
  });

  it('follows the cursor until the last page and returns every channel in order', async () => {
    const fetcher = fetcherReplying(
      jsonResponse(200, { items: [{ id: 'a' }], nextCursor: 'cursor-2' }),
      jsonResponse(200, { items: [{ id: 'b' }], nextCursor: null }),
    );

    const channels = await listChannels(fetcher);

    expect(channels.map((item) => item.id)).toEqual(['a', 'b']);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(callOf(fetcher, 0).url).toMatch(/\/channels\?limit=100$/);
    expect(callOf(fetcher, 1).url).toMatch(/\/channels\?limit=100&cursor=cursor-2$/);
  });

  it('stops when the server repeats a cursor instead of looping', async () => {
    const page = { items: [{ id: 'a' }], nextCursor: 'same' };
    const fetcher = fetcherReplying(jsonResponse(200, page), jsonResponse(200, page));

    const channels = await listChannels(fetcher);

    expect(fetcher, 'the repeated cursor ends the walk after its second request').toHaveBeenCalledTimes(2);
    expect(channels).toHaveLength(2);
  });
});

describe('startConnection', () => {
  it('posts the platform and returns the consent URL the browser is sent to', async () => {
    const consent = 'https://accounts.google.com/o/oauth2/v2/auth?state=abc';
    const fetcher = fetcherReplying(jsonResponse(202, { authorizationUrl: consent }));

    const url = await startConnection('youtube', fetcher);

    expect(url).toBe(consent);
    const { url: path, init } = callOf(fetcher);
    expect(path).toMatch(/\/channels\/connections$/);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ platform: 'youtube' });
  });

  it('refuses a consent address that is not a web address', async () => {
    const fetcher = fetcherReplying(jsonResponse(202, { authorizationUrl: 'javascript:alert(1)' }));

    await expect(startConnection('youtube', fetcher)).rejects.toThrow(/not a web address/);
  });
});

describe('startReconnection', () => {
  it('posts to the consent path of the channel and returns its consent URL', async () => {
    const fetcher = fetcherReplying(jsonResponse(202, { authorizationUrl: 'https://consent.example/start' }));

    const url = await startReconnection('6f1c2a3e-0000-4000-8000-000000000002', fetcher);

    expect(url).toBe('https://consent.example/start');
    const { url: path, init } = callOf(fetcher);
    expect(path).toMatch(/\/channels\/6f1c2a3e-0000-4000-8000-000000000002\/consent$/);
    expect(init.method).toBe('POST');
  });
});

describe('disconnectChannel', () => {
  it('sends a DELETE and returns what the platform did with the grant', async () => {
    const fetcher = fetcherReplying(jsonResponse(200, { revocation: 'failed' }));

    const revocation = await disconnectChannel('channel-1', fetcher);

    expect(revocation).toBe('failed');
    const { url, init } = callOf(fetcher);
    expect(url).toMatch(/\/channels\/channel-1$/);
    expect(init.method).toBe('DELETE');
  });
});

describe('consentUrl', () => {
  it('accepts http and https addresses', () => {
    expect(consentUrl({ authorizationUrl: 'https://a.example/x' })).toBe('https://a.example/x');
    expect(consentUrl({ authorizationUrl: 'http://localhost:9/x' })).toBe('http://localhost:9/x');
  });

  it('rejects a missing address', () => {
    expect(() => consentUrl(undefined)).toThrow(/did not return a consent address/);
  });

  it('rejects a data address', () => {
    expect(() => consentUrl({ authorizationUrl: 'data:text/html,hello' })).toThrow(/not a web address/);
  });
});
