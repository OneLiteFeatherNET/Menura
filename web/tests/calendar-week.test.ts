import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import WeekView from '../src/features/calendar/WeekView.vue';
import { publication } from './calendar-helpers';

const WEEK_ANCHOR = '2026-11-11';

function mountWeek(publications: ReturnType<typeof publication>[]) {
  return mount(WeekView, {
    props: { publications, anchorKey: WEEK_ANCHOR, colours: new Map(), selectedId: null },
  });
}

describe('week view placement', () => {
  it('places an entry in the cell of its Berlin hour, not of its UTC hour', () => {
    // 16:30 UTC is 17:30 in Berlin on Tuesday 10 Nov (winter time).
    const wrapper = mountWeek([publication({ publishAt: '2026-11-10T16:30:00Z' })]);

    const cell = wrapper.find('[data-day="2026-11-10"][data-hour="17"]');
    expect(cell.text(), 'entry in the 17 o’clock cell of Tuesday').toContain('Cilium in 30 s');
    expect(wrapper.find('[data-day="2026-11-10"][data-hour="16"]').text(), 'no entry at 16 o’clock').not.toContain(
      'Cilium in 30 s',
    );
  });

  it('places an entry of a Berlin day in that day even when the UTC day is the day before', () => {
    // 23:30 UTC on Monday 9 Nov is 00:30 on Tuesday 10 Nov in Berlin: outside the grid hours.
    const wrapper = mountWeek([publication({ publishAt: '2026-11-09T23:30:00Z' })]);

    expect(wrapper.find('[data-day="2026-11-09"]').text(), 'no entry on the Monday cells').not.toContain(
      'Cilium in 30 s',
    );
    expect(wrapper.text(), 'listed below the grid').toContain('Außerhalb 08–23 Uhr');
  });

  it('lists an entry before 08:00 below the grid instead of in a cell', () => {
    const wrapper = mountWeek([publication({ publishAt: '2026-11-10T04:30:00Z' })]);

    expect(wrapper.find('[data-hour="5"]').exists(), 'no 05 o’clock row').toBe(false);
    expect(wrapper.text(), 'listed below the grid').toContain('Cilium in 30 s');
  });
});
