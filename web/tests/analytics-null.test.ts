import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import OverviewTab from '../src/features/analytics/OverviewTab.vue';
import { formatMetricValue } from '../src/features/analytics/format';
import { stubAnalyticsFetch } from './analytics-fixtures';

describe('values a platform does not deliver', () => {
  it('formats null as "nicht verfügbar" for every metric, never as 0', () => {
    expect(formatMetricValue('views', null)).toBe('nicht verfügbar');
    expect(formatMetricValue('avgViewPercent', null)).toBe('nicht verfügbar');
    expect(formatMetricValue('avgWatchSeconds', null)).toBe('nicht verfügbar');
  });

  it('keeps a real zero as zero', () => {
    expect(formatMetricValue('likes', 0)).toBe('0');
  });

  it('formats counts and percentages in de-DE', () => {
    expect(formatMetricValue('views', 1234)).toBe('1.234');
    expect(formatMetricValue('avgViewPercent', 71.5)).toBe('71,5 %');
    expect(formatMetricValue('avgWatchSeconds', 12.25)).toBe('12,3 s');
  });
});

describe('overview tile of a metric that the selected platform lacks', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows "nicht verfügbar" with the platform note, and not 0', async () => {
    stubAnalyticsFetch((url) => {
      if (url.pathname.endsWith('/aggregate') && url.searchParams.get('metric') === 'engagedViews') {
        return { metric: 'engagedViews', groupBy: 'day', offset: '+24h', rows: [], missing: ['tt-de'] };
      }
      return undefined;
    });

    const wrapper = mount(OverviewTab, { props: { channelIds: ['tt-de'] } });
    await flushPromises();

    const tile = wrapper.find('[aria-label="Engaged Views"]');
    expect(tile.text()).toContain('nicht verfügbar');
    expect(tile.text()).toContain('nicht für TikTok verfügbar');
    expect(tile.text()).not.toMatch(/\b0\b/);
  });
});
