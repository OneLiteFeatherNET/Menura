import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import { createAppRouter } from '../src/router';
import { queryFromSelection, selectedFromQuery } from '../src/features/analytics/channelQuery';
import { CHANNELS, stubAnalyticsFetch } from './analytics-fixtures';

const ALL = CHANNELS.map((channel) => channel.id);
const Root = defineComponent({ render: () => h(RouterView) });

describe('channel selection in the query', () => {
  it('selects every channel when the parameter is absent', () => {
    expect(selectedFromQuery(undefined, ALL)).toEqual(ALL);
  });

  it('selects no channel for an empty parameter', () => {
    expect(selectedFromQuery('', ALL)).toEqual([]);
  });

  it('reads the comma list in channel order and drops unknown ids', () => {
    expect(selectedFromQuery('ig-de,xx-de,yt-de', ALL)).toEqual(['yt-de', 'ig-de']);
  });

  it('writes the selection as a comma list in channel order', () => {
    expect(queryFromSelection(['ig-de', 'yt-de'], ALL)).toBe('yt-de,ig-de');
  });

  it('drops the parameter when every channel is selected, because that is the default', () => {
    expect(queryFromSelection(ALL, ALL)).toBeUndefined();
  });

  it('writes an empty value when no channel is selected', () => {
    expect(queryFromSelection([], ALL)).toBe('');
  });

  it('round-trips a selection through the router query', () => {
    const written = queryFromSelection(['yt-de', 'tt-de'], ALL);

    expect(selectedFromQuery(written, ALL)).toEqual(['yt-de', 'tt-de']);
  });
});

describe('channel chips in the analytics layout', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('starts from the query, toggles a channel into the query and reopens with the same chips', async () => {
    stubAnalyticsFetch();
    const router = createAppRouter(createMemoryHistory());
    await router.push('/analytics?kanaele=yt-de,tt-de');
    await router.isReady();

    const wrapper = mount(Root, { global: { plugins: [router] } });
    await flushPromises();

    expect(pressed(wrapper)).toEqual({ YouTube: true, TikTok: true, Instagram: false });

    await chip(wrapper, 'TikTok').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query.kanaele, 'query after toggling TikTok off').toBe('yt-de');

    await chip(wrapper, 'Instagram').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query.kanaele, 'query after adding Instagram').toBe('yt-de,ig-de');

    const reopened = createAppRouter(createMemoryHistory());
    await reopened.push(router.currentRoute.value.fullPath);
    await reopened.isReady();
    const again = mount(Root, { global: { plugins: [reopened] } });
    await flushPromises();
    expect(pressed(again), 'chips after reopening the same link').toEqual({
      YouTube: true,
      TikTok: false,
      Instagram: true,
    });
  });

  it('keeps the channel chips on the tabs other than the overview', async () => {
    stubAnalyticsFetch();
    const router = createAppRouter(createMemoryHistory());
    await router.push('/analytics/uhrzeit?kanaele=ig-de');
    await router.isReady();

    const wrapper = mount(Root, { global: { plugins: [router] } });
    await flushPromises();

    expect(pressed(wrapper)).toEqual({ YouTube: false, TikTok: false, Instagram: true });
  });
});

function chip(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('.chip').find((button) => button.text() === label);
  if (!match) {
    throw new Error(`no chip named ${label}`);
  }
  return match;
}

function pressed(wrapper: ReturnType<typeof mount>): Record<string, boolean> {
  return Object.fromEntries(
    wrapper.findAll('.chip').map((button) => [button.text(), button.attributes('aria-pressed') === 'true']),
  );
}
