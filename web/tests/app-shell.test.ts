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

  it('renders the six navigation items of the rail in order, without phases or a roadmap', async () => {
    const wrapper = await mountShell(['VIEWER']);

    const items = wrapper.findAll('.rail .rail-item').map((item) => item.text());
    expect(items).toEqual(['Übersicht', 'Pool', 'Kalender', 'Serien', 'Analytics', 'Brand-Kit']);
    expect(wrapper.find('.rail').text(), 'no phase label').not.toContain('Phase');
    expect(wrapper.find('.rail').text(), 'no roadmap').not.toContain('Roadmap');
  });

  it('lists the navigation items in one list without group headings', async () => {
    const wrapper = await mountShell(['VIEWER']);

    expect(wrapper.findAll('.rail h2')).toHaveLength(0);
    expect(wrapper.findAll('.rail ul')).toHaveLength(1);
  });

  it('marks the brand kit as active on the render bundle sub-page and not the overview', async () => {
    const wrapper = await mountShell(['VIEWER'], '/brand-kit/render-bundle');

    const brandKit = wrapper.findAll('.rail-item').find((link) => link.text() === 'Brand-Kit');
    const overview = wrapper.findAll('.rail-item').find((link) => link.text() === 'Übersicht');
    expect(brandKit?.classes()).toContain('is-active');
    expect(overview?.classes()).not.toContain('is-active');
  });

  it('renders the routed page in the main content area', async () => {
    const wrapper = await mountShell(['VIEWER']);

    expect(wrapper.find('main').text()).toContain('Inhalt der Übersicht');
  });
});
