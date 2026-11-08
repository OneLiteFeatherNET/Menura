import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { computed, defineComponent, h } from 'vue';
import { SESSION_KEY } from '../src/auth/session';
import PoolView from '../src/features/pool/PoolView.vue';
import type { Asset, Person } from '../src/features/pool/types';

const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: true } },
  setup(_props, { slots }) {
    return () => h('a', null, slots.default?.());
  },
});

const PEOPLE: Person[] = [
  { id: 'p-a', name: 'Demo-Person A' },
  { id: 'p-c', name: 'Demo-Person C' },
];

const ASSETS: Asset[] = [
  {
    id: 'a-104',
    kind: 'clip',
    title: 'Cilium in 30 s: Policy-Check',
    ownerId: 'p-c',
    people: ['p-c'],
    tags: ['cilium', 'kubernetes'],
    durationSeconds: 45,
    createdAt: '2026-11-05T21:00:00Z',
    sourceAssetId: 'a-102',
    seriesId: 'k8s-30s',
    stage: 'rendered',
    transcript: 'ready',
    sizeBytes: 33_300_000,
  },
];

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

/** Records every request; the assets answer is the list above. */
function stubFetch(): string[] {
  const urls: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn((input: string) => {
      urls.push(input);
      if (input === '/api/prototype/people') {
        return Promise.resolve(json(PEOPLE));
      }
      if (input.startsWith('/api/prototype/assets')) {
        return Promise.resolve(json(ASSETS));
      }
      return Promise.resolve(new Response(null, { status: 404 }));
    }),
  );
  return urls;
}

function mountPool(roles: string[]) {
  return mount(PoolView, {
    global: {
      provide: {
        [SESSION_KEY as symbol]: computed(() => ({ subject: 'anna', roles: roles as never })),
      },
      stubs: { RouterLink: RouterLinkStub },
    },
  });
}

/** Sets a control's value as the browser would, then fires its event. */
function setValue(element: Element, value: string, event: 'change' | 'input'): void {
  (element as HTMLInputElement).value = value;
  element.dispatchEvent(new Event(event));
}

describe('add dialog visibility', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    stubFetch();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('is not offered to a viewer', async () => {
    const wrapper = mountPool(['VIEWER']);
    await flushPromises();

    expect(wrapper.text()).not.toContain('Material hinzufügen');
  });

  it('is offered to an editor', async () => {
    const wrapper = mountPool(['EDITOR']);
    await flushPromises();

    expect(wrapper.text()).toContain('Material hinzufügen');
  });
});

describe('filter parameters', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('sends the chosen kind with the list request', async () => {
    const urls = stubFetch();
    const wrapper = mountPool(['VIEWER']);
    await flushPromises();

    setValue(wrapper.get('[data-testid="filter-kind"]').element, 'clip', 'change');
    await flushPromises();

    expect(urls[urls.length - 1]).toContain('kind=clip');
  });

  it('sends the search text only after the 250 ms debounce', async () => {
    const urls = stubFetch();
    const wrapper = mountPool(['VIEWER']);
    await flushPromises();
    const before = urls.length;

    setValue(wrapper.get('[data-testid="filter-search"]').element, 'stabil', 'input');
    await flushPromises();
    vi.advanceTimersByTime(249);
    await flushPromises();
    expect(urls.slice(before).some((url) => url.includes('q='))).toBe(false);

    vi.advanceTimersByTime(1);
    await flushPromises();
    expect(urls.slice(before).some((url) => url.includes('q=stabil'))).toBe(true);
  });

  it('sends the chosen tag as the tag parameter', async () => {
    const urls = stubFetch();
    const wrapper = mountPool(['VIEWER']);
    await flushPromises();

    await wrapper.findAll('[data-testid="tag-chip"]').find((chip) => chip.text() === 'cilium')?.trigger('click');
    await flushPromises();

    expect(urls[urls.length - 1]).toContain('tag=cilium');
  });
});
