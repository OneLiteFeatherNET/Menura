import { describe, expect, it, vi } from 'vitest';
import {
  fetchBrandKit,
  isVisibleAt,
  parameterProblems,
  sampleValues,
  slotBox,
  tokenStyle,
} from '../src/brand/brandKit';
import { sampleShort, sampleVersion } from '../src/brand/sampleKit';

// The fetcher is injected, so these tests never reach the network.
function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('fetchBrandKit', () => {
  it('returns the newest version and sends the session cookie', async () => {
    const fetcher = vi.fn(async () => jsonResponse(200, sampleVersion));

    const version = await fetchBrandKit(fetcher);

    expect(version.version).toBe(1);
    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/brand-kit$/);
    expect(init.credentials, 'the browser sends its session cookie to the API host').toBe('include');
  });

  it('fails for a refused read so the view can offer a retry', async () => {
    const fetcher = async () => jsonResponse(403, { status: 403 });

    await expect(fetchBrandKit(fetcher)).rejects.toThrow(/403/);
  });
});

describe('tokenStyle', () => {
  it('maps every colour token to a custom property of the chosen role', () => {
    expect(tokenStyle(sampleVersion.kit, 'light')['--brand-primary']).toBe('#6638e4');
    expect(tokenStyle(sampleVersion.kit, 'dark')['--brand-primary']).toBe('#ccbdff');
  });
});

describe('slotBox', () => {
  it('places a slot in percent of the frame', () => {
    const title = sampleShort.slots.find((slot) => slot.name === 'title')!;

    expect(slotBox(title)).toEqual({ left: '4%', top: '22%', width: '92%', height: '14%' });
  });
});

describe('isVisibleAt', () => {
  it('shows the title only from 0 to 3 seconds of a short', () => {
    const title = sampleShort.slots.find((slot) => slot.name === 'title')!.timing;

    expect(isVisibleAt(title, 2.5, 30), 'visible at 2.5 seconds').toBe(true);
    expect(isVisibleAt(title, 3.5, 30), 'not visible at 3.5 seconds').toBe(false);
  });

  it('shows the call to action only in the last two seconds', () => {
    const cta = sampleShort.slots.find((slot) => slot.name === 'cta')!.timing;

    expect(isVisibleAt(cta, 20, 30), 'not visible at 20 seconds').toBe(false);
    expect(isVisibleAt(cta, 28.5, 30), 'visible at 28.5 seconds').toBe(true);
  });

  it('shows the badge for the whole duration', () => {
    const badge = sampleShort.slots.find((slot) => slot.name === 'badge')!.timing;

    expect(isVisibleAt(badge, 0, 30)).toBe(true);
    expect(isVisibleAt(badge, 29, 30)).toBe(true);
  });
});

describe('preview parameters', () => {
  it('samples a value for each declared parameter and none for an undeclared one', () => {
    const values = sampleValues(sampleShort);

    expect(Object.keys(values).sort()).toEqual(['image', 'join_domain', 'subtitle', 'title']);
    expect(values.title).toBe('Beispieltitel');
  });

  it('refuses a value for a parameter the template does not declare', () => {
    const problems = parameterProblems(sampleShort, { ...sampleValues(sampleShort), outro: 'x' });

    expect(problems).toEqual(['Der Parameter outro ist in dieser Vorlage nicht deklariert.']);
  });

  it('names a required parameter that has no value', () => {
    const values = sampleValues(sampleShort);
    delete values.join_domain;

    expect(parameterProblems(sampleShort, values)).toEqual(['Der Parameter join_domain fehlt.']);
  });

  it('accepts the sample values of the template', () => {
    expect(parameterProblems(sampleShort, sampleValues(sampleShort))).toEqual([]);
  });
});
