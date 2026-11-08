import { describe, expect, it, vi } from 'vitest';
import {
  NO_PLAYERS_WARNING,
  coverageLabel,
  createSeries,
  fetchSeriesOverview,
  warningText,
  type SeriesDraft,
  type WeekCoverage,
} from '../src/api/series';

const draft: SeriesDraft = {
  name: 'Kubernetes in 30 s',
  badgeText: { de: 'Kurz erklärt' },
  colourToken: 'primary',
  badgeToken: 'on-primary',
  openingFormula: 'Kurz erklärt:',
  layoutTemplate: 'short-9x16',
  hashtag: '#kubernetes30s',
  audience: 'developers',
  weekday: 1,
  postingSlot: 18,
  active: true,
};

function answer(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function week(overrides: Partial<WeekCoverage>): WeekCoverage {
  return {
    isoWeek: '2026-W45',
    weekStart: '2026-11-02',
    shorts: 10,
    covered: 9,
    noSeries: 1,
    percent: 90,
    flagged: false,
    ...overrides,
  };
}

describe('series client', () => {
  it('creates a series with the session cookie, a JSON body and no token', async () => {
    const fetcher = vi.fn(async () => answer({ id: 's-1', ...draft }, 201));

    await createSeries(draft, fetcher);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url, 'the request names the series collection').toMatch(/\/series$/);
    expect(init.method, 'a series is created with POST').toBe('POST');
    expect(init.credentials, 'the session cookie travels with the request').toBe('include');
    expect(init.headers, 'no authorization header is set by the browser client').not.toHaveProperty('Authorization');
    expect(JSON.parse(String(init.body)), 'the body is the draft as sent').toEqual(draft);
  });

  it('shows the reason the server gives for a refused series', async () => {
    const fetcher = vi.fn(async () =>
      answer({ title: 'Insufficient contrast', detail: 'Das Kontrastverhältnis reicht nicht aus.' }, 400),
    );

    await expect(createSeries(draft, fetcher)).rejects.toThrow('Das Kontrastverhältnis reicht nicht aus.');
  });

  it('falls back to a fixed sentence when a failure carries no detail', async () => {
    const fetcher = vi.fn(async () => new Response('<html>', { status: 500 }));

    await expect(createSeries(draft, fetcher)).rejects.toThrow('Die Serie konnte nicht gespeichert werden.');
  });

  it('reads the overview for the requested number of weeks', async () => {
    const fetcher = vi.fn(async () => answer({ series: [], warnings: [], coverage: [] }));

    await fetchSeriesOverview(4, fetcher);

    const [url] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url, 'the overview names the weeks it reports').toMatch(/\/series\/overview\?weeks=4$/);
  });
});

describe('series labels', () => {
  it('names the share, the counts and the week of a week with shorts', () => {
    expect(coverageLabel(week({ percent: 80, covered: 8, shorts: 10 })), 'the share is shown in words')
      .toBe('80 % · 8 von 10 zugeordnet');
  });

  it('says that a week without published shorts has none', () => {
    expect(coverageLabel(week({ shorts: 0, covered: 0, noSeries: 0, percent: null })),
      'a week without shorts has no percentage').toBe('Keine Shorts');
  });

  it('gives the audience warning its German text', () => {
    expect(warningText(NO_PLAYERS_WARNING)).toContain('Keine aktive Serie richtet sich an Spieler');
  });

  it('shows an unknown warning code as it is rather than dropping it', () => {
    expect(warningText('something_new'), 'a warning the browser does not know is still shown').toBe('something_new');
  });
});
