import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MetricsTab from '../src/features/analytics/MetricsTab.vue';
import { METRICS, stubAnalyticsFetch } from './analytics-fixtures';

const PLATFORMS = ['youtube', 'tiktok', 'instagram'] as const;

function rowOf(wrapper: ReturnType<typeof mount>, label: string) {
  const row = wrapper.findAll('tbody tr').find((tr) => tr.find('td').text() === label);
  if (!row) {
    throw new Error(`no row for ${label}`);
  }
  return row;
}

describe('Datenpunkte table', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('has one row per catalogue metric, in catalogue order', async () => {
    stubAnalyticsFetch();

    const wrapper = mount(MetricsTab);
    await flushPromises();

    const labels = wrapper.findAll('tbody tr').map((tr) => tr.find('td').text());
    expect(labels).toEqual(METRICS.map((metric) => metric.label));
  });

  it('marks each platform cell as "verfügbar" exactly when the catalogue lists the platform', async () => {
    stubAnalyticsFetch();

    const wrapper = mount(MetricsTab);
    await flushPromises();

    for (const metric of METRICS) {
      const cells = rowOf(wrapper, metric.label).findAll('td').slice(1);
      PLATFORMS.forEach((platform, index) => {
        const expected = metric.platforms.includes(platform) ? 'verfügbar' : 'nicht verfügbar';
        expect(cells[index]?.text(), `${metric.id} on ${platform}`).toBe(expected);
      });
    }
  });

  it('names the API each platform reads from, one line per platform', async () => {
    stubAnalyticsFetch();

    const wrapper = mount(MetricsTab);
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain('YouTube: YouTube Analytics API');
    expect(text).toContain('TikTok: TikTok Display API');
    expect(text).toContain('Instagram: Instagram Graph API Insights');
  });

  it('asks the catalogue endpoint once per mount', async () => {
    const fake = stubAnalyticsFetch();

    mount(MetricsTab);
    await flushPromises();

    const urls = fake.mock.calls.map(([input]) => input);
    expect(urls.filter((url) => url === '/api/prototype/analytics/metrics')).toHaveLength(1);
  });
});
