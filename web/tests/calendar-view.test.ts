import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { computed, type ComputedRef } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import type { Session } from '../src/api/types';
import { SESSION_KEY } from '../src/auth/session';
import CalendarView from '../src/features/calendar/CalendarView.vue';
import { NOW_KEY } from '../src/features/calendar/now';
import { baseRoutes, fetchRoutes, NOW, publication, publishedPost } from './calendar-helpers';

async function mountCalendar(path: string, roles: Session['roles'], routes: ReturnType<typeof baseRoutes>) {
  vi.stubGlobal('fetch', vi.fn(fetchRoutes(routes)));
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/kalender', name: 'calendar', component: CalendarView }],
  });
  await router.push(path);
  await router.isReady();

  const session: ComputedRef<Session | null> = computed(() => ({ subject: 'tester', roles }));
  const wrapper = mount(CalendarView, {
    global: {
      plugins: [router],
      provide: {
        [SESSION_KEY as symbol]: session,
        [NOW_KEY as symbol]: () => NOW,
      },
    },
  });
  await flushPromises();
  return wrapper;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('calendar tabs', () => {
  it('opens on the week tab when the query names no view', async () => {
    const wrapper = await mountCalendar('/kalender', ['EDITOR'], baseRoutes([publication()]));

    const selected = wrapper.findAll('[role="tab"][aria-selected="true"]').map((tab) => tab.text());
    expect(selected, 'selected tab').toEqual(['Woche']);
  });

  it('shows the status of each entry as its label', async () => {
    const wrapper = await mountCalendar('/kalender?ansicht=liste', ['EDITOR'], baseRoutes([
      publication({ id: 'pub-1', status: 'planned' }),
      publication({ id: 'pub-2', channelId: 'tt-de', platform: 'tiktok', status: 'failed', mode: 'hub',
        steps: [] }),
      publishedPost(),
    ]));

    expect(wrapper.text()).toContain('Geplant');
    expect(wrapper.text()).toContain('Fehlgeschlagen');
    expect(wrapper.text()).toContain('Veröffentlicht');
  });
});

describe('plan dialog from the route', () => {
  it('opens the plan dialog with the clip of the query preselected', async () => {
    const wrapper = await mountCalendar('/kalender?plan=a-104', ['EDITOR'], baseRoutes([]));

    const dialog = wrapper.find('[role="dialog"]');
    expect(dialog.exists(), 'dialog is open').toBe(true);
    expect((dialog.find('select[name="clip"]').element as HTMLSelectElement).value).toBe('a-104');
    expect(dialog.text(), 'prefilled preview of the title').toContain('Cilium in 30 s: Policy-Check');
  });

  it('does not open the plan dialog for a viewer, even with the query', async () => {
    const wrapper = await mountCalendar('/kalender?plan=a-104', ['VIEWER'], baseRoutes([]));

    expect(wrapper.find('[role="dialog"]').exists(), 'no dialog').toBe(false);
    expect(wrapper.text(), 'no plan action').not.toContain('Clip einplanen');
  });

  it('offers no edit, delete or plan actions to a viewer', async () => {
    const wrapper = await mountCalendar('/kalender?ansicht=liste', ['VIEWER'], baseRoutes([publication()]));

    await wrapper.find('[data-entry]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).not.toContain('Löschen');
    expect(wrapper.text()).not.toContain('Termin speichern');
    expect(wrapper.text()).not.toContain('Clip einplanen');
  });
});
