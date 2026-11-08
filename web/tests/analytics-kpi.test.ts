import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import OverviewTab from '../src/features/analytics/OverviewTab.vue';
import {
  comparisonWindows,
  deltaPercent,
  formatDelta,
  totalOf,
  unavailableNote,
} from '../src/features/analytics/format';
import { stubAnalyticsFetch } from './analytics-fixtures';

const NOW = new Date('2026-11-08T12:00:00Z');

describe('KPI delta', () => {
  it('is the relative change against the period before', () => {
    expect(deltaPercent(120, 100)).toBe(20);
    expect(deltaPercent(80, 100)).toBe(-20);
  });

  it('is absent when there is no basis for a comparison', () => {
    expect(deltaPercent(5, 0)).toBeNull();
    expect(deltaPercent(null, 100)).toBeNull();
    expect(deltaPercent(100, null)).toBeNull();
  });

  it('reads with a sign and one decimal place in de-DE', () => {
    expect(formatDelta(20)).toBe('+20 %');
    expect(formatDelta(-12.5)).toBe('-12,5 %');
    expect(formatDelta(null)).toBe('ohne Vergleichswert');
  });
});

describe('comparison windows', () => {
  it('compares the last seven days with the seven days before them', () => {
    const windows = comparisonWindows(NOW);

    expect(windows.current).toEqual({ from: '2026-11-01T12:00:00.000Z', to: '2026-11-08T12:00:00.000Z' });
    expect(windows.previous).toEqual({ from: '2026-10-25T12:00:00.000Z', to: '2026-11-01T12:00:00.000Z' });
  });
});

describe('total of a period', () => {
  it('sums counts over the rows', () => {
    expect(totalOf('views', [
      { key: '2026-11-05', value: 100, posts: 1 },
      { key: '2026-11-06', value: 20, posts: 1 },
    ])).toBe(120);
  });

  it('averages percentages weighted by the posts behind each row', () => {
    expect(totalOf('avgViewPercent', [
      { key: '2026-11-05', value: 70, posts: 1 },
      { key: '2026-11-06', value: 40, posts: 3 },
    ])).toBe(47.5);
  });

  it('is null for a period without rows, so an empty period is not 0', () => {
    expect(totalOf('views', [])).toBeNull();
  });
});

describe('unavailable note', () => {
  it('names one platform', () => {
    expect(unavailableNote(['tiktok'])).toBe('nicht für TikTok verfügbar');
  });

  it('names two platforms with "und"', () => {
    expect(unavailableNote(['tiktok', 'instagram'])).toBe('nicht für TikTok und Instagram verfügbar');
  });
});

describe('overview tile with a delta', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('shows the change of views against the seven days before', async () => {
    stubAnalyticsFetch((url) => {
      if (!url.pathname.endsWith('/aggregate') || url.searchParams.get('metric') !== 'views') {
        return undefined;
      }
      const current = url.searchParams.get('from') === '2026-11-01T12:00:00.000Z';
      return {
        metric: 'views',
        groupBy: 'day',
        offset: '+24h',
        rows: current
          ? [{ key: '2026-11-05', value: 120, posts: 2 }]
          : [{ key: '2026-10-27', value: 100, posts: 2 }],
        missing: [],
      };
    });

    const wrapper = mount(OverviewTab, { props: { channelIds: ['yt-de'] } });
    await flushPromises();

    const tile = wrapper.find('[aria-label="Aufrufe"]');
    expect(tile.text()).toContain('120');
    expect(tile.text()).toContain('+20 %');
    expect(tile.text()).toContain('gegenüber 100');
  });
});
