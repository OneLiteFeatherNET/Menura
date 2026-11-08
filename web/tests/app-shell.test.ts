import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import App from '../src/App.vue';

// The shell reads the session on mount. The fetch is replaced, so no test reaches the network.
function stubSession(status: number, body?: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status })),
  );
}

describe('app shell', () => {
  beforeEach(() => stubSession(401));
  afterEach(() => vi.unstubAllGlobals());

  it('renders a top app bar that shows the product name', () => {
    const wrapper = mount(App);

    expect(wrapper.find('header').text()).toContain('Menura');
  });

  it('renders an empty main content area below the app bar', () => {
    const wrapper = mount(App);

    expect(wrapper.find('main').exists()).toBe(true);
  });

  it('offers a sign-in link to an anonymous visitor', async () => {
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('a').text()).toBe('Anmelden');
    expect(wrapper.find('[data-testid="session-role"]').exists()).toBe(false);
  });

  it('offers no navigation to an anonymous visitor', async () => {
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('nav').exists()).toBe(false);
  });

  it('offers the Kanäle entry to a signed-in person', async () => {
    stubSession(200, { subject: 'e2e-admin-1', roles: ['ADMIN'] });
    const wrapper = mount(App);
    await flushPromises();

    const link = wrapper.find('nav a');
    expect(link.text()).toBe('Kanäle');
    expect(link.attributes('href')).toBe('/channels');
  });

  it('shows the German name of the signed-in role', async () => {
    stubSession(200, { subject: 'e2e-editor-1', roles: ['EDITOR'] });
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('[data-testid="session-role"]').text()).toBe('Redaktion');
  });
});
