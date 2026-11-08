import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PublicationPanel from '../src/features/calendar/PublicationPanel.vue';
import { CHANNELS, PLATFORMS, fetchRoutes, jsonResponse, publication, publishedPost } from './calendar-helpers';

const SNAPSHOT = {
  offset: '+1h',
  at: '2026-11-01T18:00:00Z',
  views: null,
  engagedViews: 80,
  likes: 9,
  comments: 1,
  shares: 2,
};

function mountPanel(publicationValue: ReturnType<typeof publication>, canEdit: boolean) {
  return mount(PublicationPanel, {
    props: {
      publication: publicationValue,
      series: null,
      channel: CHANNELS[0],
      capability: PLATFORMS.youtube,
      canEdit,
    },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('publication panel metrics', () => {
  it('shows a null metric as "nicht verfügbar", never as zero', async () => {
    vi.stubGlobal('fetch', fetchRoutes({
      'GET /api/prototype/analytics/posts/p-41/snapshots': () => jsonResponse(200, [SNAPSHOT]),
    }));

    const wrapper = mountPanel(publishedPost(), false);
    await flushPromises();

    expect(wrapper.text(), 'views is not available').toContain('nicht verfügbar');
    expect(wrapper.find('[data-metric="views"]').text()).toBe('nicht verfügbar');
  });

  it('says "Noch keine Messwerte" when the post has no snapshots yet', async () => {
    vi.stubGlobal('fetch', fetchRoutes({
      'GET /api/prototype/analytics/posts/p-41/snapshots': () => jsonResponse(404, { status: 404, title: 'Not Found' }),
    }));

    const wrapper = mountPanel(publishedPost(), false);
    await flushPromises();

    expect(wrapper.text()).toContain('Noch keine Messwerte');
  });

  it('shows the steps in the order upload, planen, metadaten, veröffentlicht, metriken', async () => {
    vi.stubGlobal('fetch', fetchRoutes({}));

    const wrapper = mountPanel(publication(), false);
    await flushPromises();

    const labels = wrapper.findAll('[data-step]').map((step) => step.attributes('data-step'));
    expect(labels).toEqual(['upload', 'schedule', 'metadata', 'publish', 'metrics']);
    expect(wrapper.text()).toContain('Planen');
    expect(wrapper.text()).toContain('Metriken');
  });
});

describe('publication panel for a viewer', () => {
  it('offers neither editing nor deleting', async () => {
    vi.stubGlobal('fetch', fetchRoutes({}));

    const wrapper = mountPanel(publication(), false);
    await flushPromises();

    expect(wrapper.text(), 'no delete action').not.toContain('Löschen');
    expect(wrapper.text(), 'no save action').not.toContain('Termin speichern');
  });

  it('deletes a planned publication only after an inline confirmation', async () => {
    const fetchSpy = vi.fn(fetchRoutes({
      'DELETE /api/prototype/publications/pub-1': () => jsonResponse(204, undefined),
    }));
    const confirmSpy = vi.fn();
    vi.stubGlobal('confirm', confirmSpy);
    vi.stubGlobal('fetch', fetchSpy);

    const wrapper = mountPanel(publication(), true);
    await flushPromises();
    const deleteButton = wrapper.findAll('md-text-button').find((b) => b.text() === 'Löschen');
    await deleteButton?.trigger('click');

    expect(fetchSpy, 'nothing is deleted before the confirmation').not.toHaveBeenCalledWith(
      '/api/prototype/publications/pub-1',
      expect.objectContaining({ method: 'DELETE' }),
    );
    expect(wrapper.text(), 'inline confirmation').toContain('Ja, löschen');
    expect(confirmSpy, 'no window.confirm').not.toHaveBeenCalled();
  });
});
