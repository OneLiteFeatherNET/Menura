import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, type RouteRecordRaw } from 'vue-router';
import type { Role } from '../src/api/types';
import AppShell from '../src/layout/AppShell.vue';
import { createAppRouter } from '../src/router';

const Stub = defineComponent({ render: () => h('p', 'Inhalt der Übersicht') });

async function mountShell(roles: Role[], path = '/') {
  const routes: RouteRecordRaw[] = [
    { path: '/', component: Stub },
    { path: '/brand-kit/render-bundle', component: Stub },
  ];
  const router = createAppRouter(createMemoryHistory(), routes);
  router.push(path);
  await router.isReady();

  return mount(AppShell, {
    props: { session: { subject: 'anna', roles } },
    global: { plugins: [router] },
  });
}

describe('app shell', () => {
  it('shows the permanent prototype banner in the top bar', async () => {
    const wrapper = await mountShell(['VIEWER']);

    expect(wrapper.find('header').text()).toContain('Prototyp · Demo-Daten');
  });

  it('shows the signed-in roles as chips', async () => {
    const wrapper = await mountShell(['VIEWER', 'EDITOR']);

    const chips = wrapper.findAll('.role-chip').map((chip) => chip.text());
    expect(chips).toEqual(['VIEWER', 'EDITOR']);
  });

  it('shows the product name as the wordmark of the navigation rail', async () => {
    const wrapper = await mountShell(['VIEWER']);

    expect(wrapper.find('.rail-brand').text()).toContain('Menura');
  });

  it('renders the group headings of the rail in order, the roadmap last', async () => {
    const wrapper = await mountShell(['VIEWER']);

    const headings = wrapper.findAll('.rail section h2').map((heading) => heading.text());
    expect(headings).toEqual(['Überblick', 'Wiedererkennung', 'Messbarkeit', 'Roadmap']);
  });

  it('labels each group list with the text of its heading', async () => {
    const wrapper = await mountShell(['VIEWER']);

    const sections = wrapper.findAll('.rail section');
    expect(sections).toHaveLength(4);
    for (const section of sections) {
      const labelId = section.find('ul').attributes('aria-labelledby');
      expect(labelId, 'the list needs aria-labelledby').toBeTruthy();
      expect(section.find(`[id="${labelId}"]`).text()).toBe(section.find('h2').text());
    }
  });

  it('marks the brand kit as active on the render bundle sub-page and not the overview', async () => {
    const wrapper = await mountShell(['VIEWER'], '/brand-kit/render-bundle');

    const brandKit = wrapper.findAll('.rail-item').find((link) => link.text() === 'Brand-Kit');
    const overview = wrapper.findAll('.rail-item').find((link) => link.text() === 'Übersicht');
    expect(brandKit?.classes()).toContain('is-active');
    expect(overview?.classes()).not.toContain('is-active');
  });

  it('greys out the later phases in the rail, each with its phase number', async () => {
    const wrapper = await mountShell(['VIEWER']);

    const later = wrapper.findAll('.rail-item.is-later');
    expect(later).toHaveLength(5);
    expect(later[0]?.text()).toContain('Planung & Kalender');
    expect(later[0]?.text()).toContain('Phase 2');
  });

  it('renders the routed page in the main content area', async () => {
    const wrapper = await mountShell(['VIEWER']);

    expect(wrapper.find('main').text()).toContain('Inhalt der Übersicht');
  });
});
