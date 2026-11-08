import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TimeTab from '../src/features/analytics/TimeTab.vue';
import { heatBackground, heatLevel } from '../src/features/analytics/chart';
import { stubAnalyticsFetch } from './analytics-fixtures';

/** The heatmap of one request: one cell with posts (Tuesday 17:00, 830 engaged views, 2 posts). */
function heatmapAnswer(url: URL) {
  if (url.pathname.endsWith('/heatmap')) {
    return {
      cells: [{ weekday: 'TUESDAY', hour: 17, value: 830, posts: 2 }],
      missing: [],
    };
  }
  return undefined;
}

function cells(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.heatmap td .cell');
}

describe('weekday and hour heatmap', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('labels a posted cell with its weekday, hour, value and number of posts', async () => {
    stubAnalyticsFetch(heatmapAnswer);

    const wrapper = mount(TimeTab, { props: { channelIds: ['yt-de', 'tt-de', 'ig-de'] } });
    await flushPromises();

    const label = 'Dienstag 17 Uhr: 830 Engaged Views, 2 Posts';
    expect(wrapper.find(`[aria-label="${label}"]`).exists(), label).toBe(true);
  });

  it('gives every cell of the week an accessible name, posted or not', async () => {
    stubAnalyticsFetch(heatmapAnswer);

    const wrapper = mount(TimeTab, { props: { channelIds: ['yt-de'] } });
    await flushPromises();

    expect(cells(wrapper)).toHaveLength(7 * 24);
    expect(cells(wrapper).every((cell) => cell.attributes('aria-label')?.length)).toBe(true);
    expect(wrapper.find('[aria-label="Montag 0 Uhr: keine Beiträge"]').exists()).toBe(true);
  });

  it('colours the posted cell darker than an empty one, from the theme primary', () => {
    expect(heatLevel(830, 830)).toBe(4);
    expect(heatLevel(null, 830)).toBe(0);
    expect(heatBackground(4)).toContain('var(--md-sys-color-primary)');
    expect(heatBackground(0)).toContain('var(--md-sys-color-surface-container)');
  });

  it('shows the note that platforms give no hourly values and that Menura measures itself', async () => {
    stubAnalyticsFetch(heatmapAnswer);

    const wrapper = mount(TimeTab, { props: { channelIds: ['yt-de'] } });
    await flushPromises();

    expect(wrapper.text()).toContain(
      'Plattformen liefern keine Stundenwerte. Menura misst selbst nach +1 h, +2 h, +4 h, +8 h, +24 h, +48 h und +7 d und vergleicht gleich alte Messstände.',
    );
  });
});
