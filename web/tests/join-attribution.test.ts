import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../src/api/client';
import type { WeeklyAttribution } from '../src/api/types';
import JoinAttributionView from '../src/features/analytics/JoinAttributionTab.vue';
import { SOURCE_ORDER, stackedColumns, summaryOf } from '../src/domain/attribution';

const WEEK_45: WeeklyAttribution = {
  week: '2026-W45',
  firstJoins: 11,
  answered: 7,
  answerRate: 0.64,
  bySource: [
    { source: 'youtube', firstJoins: 3 },
    { source: 'tiktok', firstJoins: 1 },
    { source: 'instagram', firstJoins: 0 },
    { source: 'twitch', firstJoins: 1 },
    { source: 'friends', firstJoins: 1 },
    { source: 'other', firstJoins: 1 },
    { source: 'unanswered', firstJoins: 4 },
  ],
};

const LAYOUT = { width: 960, height: 340, left: 56, right: 16, top: 32, bottom: 48 };

describe('summary of one week', () => {
  it('carries the first joins, the answers and the answer rate of the week', () => {
    const summary = summaryOf([WEEK_45], '2026-W45');

    expect(summary?.firstJoins, 'first joins').toBe(11);
    expect(summary?.answered, 'answered').toBe(7);
    expect(summary?.answerRate, 'answer rate').toBe(0.64);
  });

  it('lists every source in the fixed order, with zeros for the sources without joins', () => {
    const summary = summaryOf([WEEK_45], '2026-W45');

    expect(Object.keys(summary?.bySource ?? {})).toEqual([...SOURCE_ORDER]);
    expect(summary?.bySource.instagram, 'instagram').toBe(0);
    expect(summary?.bySource.twitch, 'twitch').toBe(1);
  });

  it('is null for a week without measured first joins', () => {
    expect(summaryOf([WEEK_45], '2026-W44')).toBeNull();
  });
});

describe('stacked columns by stated source', () => {
  it('stacks only the sources with joins and fills the column to the week total', () => {
    const { segments, maxValue } = stackedColumns(['2026-W45'], [WEEK_45], LAYOUT);
    const plotHeight = LAYOUT.height - LAYOUT.top - LAYOUT.bottom;

    expect(maxValue).toBe(11);
    expect(segments.map((segment) => segment.source)).toEqual([
      'youtube',
      'tiktok',
      'twitch',
      'friends',
      'other',
      'unanswered',
    ]);
    const stacked = segments.reduce((sum, segment) => sum + segment.height, 0);
    expect(stacked).toBeCloseTo(plotHeight, 6);
  });
});

describe('join attribution view', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the answer rate of the week next to the sources', async () => {
    vi.spyOn(api, 'weeklyAttribution').mockResolvedValue([WEEK_45]);

    const wrapper = mount(JoinAttributionView);
    await flushPromises();

    expect(wrapper.text(), 'answer rate').toContain('64 %');
    expect(wrapper.text(), 'answered of all first joins').toContain('7 von 11');
  });

  it('labels the sources as stated by the players', async () => {
    vi.spyOn(api, 'weeklyAttribution').mockResolvedValue([WEEK_45]);

    const wrapper = mount(JoinAttributionView);
    await flushPromises();

    expect(wrapper.text()).toContain('laut Spielern');
  });
});
