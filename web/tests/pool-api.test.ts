import { describe, expect, it } from 'vitest';
import { ApiError } from '../src/api/client';
import { assetQuery, createPoolApi, type FetchLike } from '../src/features/pool/api';

interface Recorded {
  url: string;
  init: RequestInit | undefined;
}

function recordingFetch(answer: () => Response): { fetcher: FetchLike; calls: Recorded[] } {
  const calls: Recorded[] = [];
  const fetcher: FetchLike = (input, init) => {
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

describe('filter parameters of the asset list', () => {
  it('sends each set filter under the contract name', () => {
    const query = assetQuery({
      kind: 'clip',
      ownerId: 'me',
      personId: 'p-c',
      tag: 'cilium',
      stage: 'rendered',
      q: 'Hubble',
    });

    const params = new URLSearchParams(query);
    expect(params.get('kind')).toBe('clip');
    expect(params.get('ownerId')).toBe('me');
    expect(params.get('personId')).toBe('p-c');
    expect(params.get('tag')).toBe('cilium');
    expect(params.get('stage')).toBe('rendered');
    expect(params.get('q')).toBe('Hubble');
  });

  it('leaves out the filters that are not set', () => {
    expect(assetQuery({})).toBe('');
    expect(assetQuery({ kind: undefined, q: '' })).toBe('');
  });

  it('requests the list with the filter in the URL and the session cookie', async () => {
    const { fetcher, calls } = recordingFetch(() => json(200, []));

    await createPoolApi(fetcher).assets({ stage: 'cut' });

    expect(calls[0]?.url).toBe('/api/prototype/assets?stage=cut');
    expect(calls[0]?.init?.credentials).toBe('include');
  });
});

describe('writes of the pool', () => {
  it('sends the tags and people as the update body', async () => {
    const { fetcher, calls } = recordingFetch(() => json(200, {}));

    await createPoolApi(fetcher).updateTags('a-104', { tags: ['cilium'], people: ['p-c'] });

    expect(calls[0]?.url).toBe('/api/prototype/assets/a-104/tags');
    expect(calls[0]?.init?.method).toBe('PUT');
    expect(JSON.parse(String(calls[0]?.init?.body))).toEqual({ tags: ['cilium'], people: ['p-c'] });
  });

  it('surfaces a 422 as an ApiError carrying the status', async () => {
    const { fetcher } = recordingFetch(() =>
      json(422, { status: 422, title: 'Unprocessable Content', detail: 'Ungültiger Tag.' }),
    );

    const failure = await createPoolApi(fetcher)
      .createAsset({ kind: 'raw', title: 'x', tags: [], people: [], durationSeconds: 60 })
      .catch((cause: unknown) => cause);

    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).status).toBe(422);
  });
});
