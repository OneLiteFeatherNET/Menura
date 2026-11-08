import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MonthView from '../src/features/calendar/MonthView.vue';
import { publication } from './calendar-helpers';

function mountMonth(publications: ReturnType<typeof publication>[]) {
  return mount(MonthView, {
    props: { publications, anchorKey: '2026-11-08', colours: new Map(), selectedId: null },
  });
}

/** Five publications on Tuesday 10 Nov at 09:00, 12:00, 15:00, 18:00 and 20:00 Berlin (UTC+1). */
function fiveOnTuesday() {
  return ['08:00', '11:00', '14:00', '17:00', '19:00'].map((utc, index) =>
    publication({ id: `pub-${index}`, metadata: { title: `Titel ${index}`, description: '', tags: [], playlist: null },
      publishAt: `2026-11-10T${utc}:00Z` }),
  );
}

describe('month view overflow', () => {
  it('shows three entries in a day and the number of the rest as "+n weitere"', () => {
    const wrapper = mountMonth(fiveOnTuesday());

    const day = wrapper.find('[data-day="2026-11-10"]');
    expect(day.findAll('[data-entry]'), 'entries shown in the day').toHaveLength(3);
    expect(day.text(), 'overflow count').toContain('+2 weitere');
  });

  it('shows no overflow link for a day with three entries or fewer', () => {
    const wrapper = mountMonth(fiveOnTuesday().slice(0, 3));

    expect(wrapper.find('[data-day="2026-11-10"]').text(), 'no overflow link').not.toContain('weitere');
  });
});
