import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import App from '../src/App.vue';
import { createAppRouter } from '../src/router';

// The shell reads the session on mount. The fetch is replaced, so no test reaches the network: the session URL gets
// the stubbed answer and every other request (a page's data) gets a 404, which the pages show as their error state.
function stubSession(status: number, body?: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) =>
      String(input).endsWith('/auth/session')
        ? new Response(body === undefined ? null : JSON.stringify(body), { status })
        : new Response(null, { status: 404 }),
    ),
  );
}

/** Mounts the app at a path of an in-memory history, with the app's routes. */
async function mountAt(path: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  const wrapper = mount(App, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe('app shell', () => {
  beforeEach(() => stubSession(401));
  afterEach(() => vi.unstubAllGlobals());

  it('renders a top app bar that shows the product name', async () => {
    const { wrapper } = await mountAt('/');

    expect(wrapper.find('header').text()).toContain('Menura');
  });

  it('renders a main content area below the app bar', async () => {
    const { wrapper } = await mountAt('/');

    expect(wrapper.find('main').exists()).toBe(true);
  });

  it('offers a sign-in link to an anonymous visitor', async () => {
    const { wrapper } = await mountAt('/');

    expect(wrapper.find('a').text()).toBe('Anmelden');
    expect(wrapper.find('[data-testid="session-role"]').exists()).toBe(false);
  });

  it('offers no navigation to an anonymous visitor, on any path', async () => {
    const { wrapper } = await mountAt('/pool');

    expect(wrapper.find('nav').exists()).toBe(false);
  });

  it('shows the German name of the signed-in role', async () => {
    stubSession(200, { subject: 'e2e-editor-1', roles: ['EDITOR'] });
    const { wrapper } = await mountAt('/');

    expect(wrapper.find('[data-testid="session-role"]').text()).toBe('Redaktion');
  });

  it('lists the areas of a viewer in the navigation, in order', async () => {
    stubSession(200, { subject: 'e2e-viewer-1', roles: ['VIEWER'] });
    const { wrapper } = await mountAt('/');

    const labels = wrapper.findAll('nav a').map((link) => link.attributes('aria-label'));
    expect(labels).toEqual(['Übersicht', 'Pool', 'Serien', 'Brand-Kit', 'Kanäle']);
  });

  it('links the Kanäle entry to the channels path, which the consent callback returns to', async () => {
    stubSession(200, { subject: 'e2e-admin-1', roles: ['ADMIN'] });
    const { wrapper } = await mountAt('/');

    const link = wrapper.find('nav a[aria-label="Kanäle"]');
    expect(link.attributes('href')).toBe('/channels');
  });

  it('marks the rail item of the current path as the current page', async () => {
    stubSession(200, { subject: 'e2e-editor-1', roles: ['EDITOR'] });
    const { wrapper } = await mountAt('/brand-kit');

    expect(wrapper.find('nav a[aria-label="Brand-Kit"]').attributes('aria-current')).toBe('page');
    expect(wrapper.find('nav a[aria-label="Pool"]').attributes('aria-current')).toBeUndefined();
  });

  it('shows the start page with the role at the root path', async () => {
    stubSession(200, { subject: 'e2e-viewer-1', roles: ['VIEWER'] });
    const { wrapper } = await mountAt('/');

    expect(wrapper.find('[data-testid="overview-role"]').text()).toBe('Betrachter');
  });

  it('navigates in the app when a rail item is chosen', async () => {
    stubSession(200, { subject: 'e2e-editor-1', roles: ['EDITOR'] });
    const { wrapper, router } = await mountAt('/');

    await wrapper.find('nav a[aria-label="Pool"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/pool');
    expect(wrapper.find('nav a[aria-label="Pool"]').attributes('aria-current')).toBe('page');
  });

  it('shows the not-found view for an unknown path of a signed-in person', async () => {
    stubSession(200, { subject: 'e2e-viewer-1', roles: ['VIEWER'] });
    const { wrapper } = await mountAt('/gibt-es-nicht');

    expect(wrapper.find('main h1').text()).toBe('Seite nicht gefunden');
  });
});
