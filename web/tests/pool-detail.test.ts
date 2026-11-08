import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h, computed } from 'vue';
import { SESSION_KEY } from '../src/auth/session';
import AssetDetailView from '../src/features/pool/AssetDetailView.vue';
import type { Asset, AssetDetail, Person } from '../src/features/pool/types';

const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: true } },
  setup(_props, { slots }) {
    return () => h('a', { class: 'router-link-stub' }, slots.default?.());
  },
});

const PEOPLE: Person[] = [
  { id: 'p-a', name: 'Demo-Person A' },
  { id: 'p-c', name: 'Demo-Person C' },
];

function asset(overrides: Partial<AssetDetail>): AssetDetail {
  return {
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
    transcriptSegments: [{ startSeconds: 0, endSeconds: 6, personId: 'p-c', text: 'Eine Policy in 30 Sekunden.' }],
    clips: [],
    ...overrides,
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

/** Answers the detail and the list of all assets by URL. */
function stubFetch(detail: AssetDetail): void {
  const catalogue: Asset[] = [detail];
  vi.stubGlobal(
    'fetch',
    vi.fn((input: string) => {
      if (input.startsWith('/api/prototype/assets/')) {
        return Promise.resolve(json(detail));
      }
      if (input.startsWith('/api/prototype/assets')) {
        return Promise.resolve(json(catalogue));
      }
      if (input === '/api/prototype/people') {
        return Promise.resolve(json(PEOPLE));
      }
      return Promise.resolve(new Response(null, { status: 404 }));
    }),
  );
}

function mountDetail(detail: AssetDetail, roles: string[] = ['EDITOR']) {
  return mount(AssetDetailView, {
    props: { id: detail.id },
    global: {
      provide: {
        [SESSION_KEY as symbol]: computed(() => ({ subject: 'anna', roles: roles as never })),
      },
      stubs: { RouterLink: RouterLinkStub },
    },
  });
}

describe('planning link of a clip', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('offers "Einplanen" for a rendered clip, pointing at the calendar with the asset', async () => {
    stubFetch(asset({ stage: 'rendered' }));

    const wrapper = mountDetail(asset({ stage: 'rendered' }));
    await flushPromises();

    const links = wrapper.findAllComponents(RouterLinkStub);
    const planning = links.find((link) => link.text() === 'Einplanen');
    expect(planning, 'a planning link exists').toBeDefined();
    expect(planning?.props('to')).toBe('/kalender?plan=a-104');
  });

  it('does not offer "Einplanen" for a clip that is only cut', async () => {
    stubFetch(asset({ stage: 'cut' }));

    const wrapper = mountDetail(asset({ stage: 'cut' }));
    await flushPromises();

    expect(wrapper.findAllComponents(RouterLinkStub).some((link) => link.text() === 'Einplanen')).toBe(false);
  });

  it('does not offer "Einplanen" for raw material', async () => {
    stubFetch(asset({ stage: 'raw', kind: 'raw', sourceAssetId: null }));

    const wrapper = mountDetail(asset({ stage: 'raw', kind: 'raw', sourceAssetId: null }));
    await flushPromises();

    expect(wrapper.findAllComponents(RouterLinkStub).some((link) => link.text() === 'Einplanen')).toBe(false);
  });
});
