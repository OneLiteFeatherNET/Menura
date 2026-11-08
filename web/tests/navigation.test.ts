import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import { createAppRouter } from '../src/router';

// The app mounts the routed page through a root RouterView; the analytics layout nests its own one below it.
const Root = defineComponent({ render: () => h(RouterView) });

describe('legacy routes', () => {
  it('redirects the old join-attribution page to its tab in analytics', async () => {
    const router = createAppRouter(createMemoryHistory());
    router.push('/join-attribution');
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe('/analytics/join-attribution');
  });
});

describe('analytics tabs', () => {
  it('renders the six tab links in order', async () => {
    const router = createAppRouter(createMemoryHistory());
    router.push('/analytics');
    await router.isReady();

    const wrapper = mount(Root, { global: { plugins: [router] } });

    const tabs = wrapper.findAll('.tab').map((tab) => tab.text());
    expect(tabs).toEqual([
      'Überblick',
      'Kanäle',
      'Uhrzeit & Wochentag',
      'Serien',
      'Join-Attribution',
      'Datenpunkte',
    ]);
  });

  it('marks only the current tab as the current page', async () => {
    const router = createAppRouter(createMemoryHistory());
    router.push('/analytics/uhrzeit');
    await router.isReady();

    const wrapper = mount(Root, { global: { plugins: [router] } });

    const current = wrapper.findAll('.tab').filter((tab) => tab.attributes('aria-current') === 'page');
    expect(current.map((tab) => tab.text())).toEqual(['Uhrzeit & Wochentag']);
  });
});
