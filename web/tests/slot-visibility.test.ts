import { describe, expect, it } from 'vitest';
import type { SlotDefinition, Template } from '../src/api/types';
import { isSlotVisible, previewRect, safeBands, SHORT_DURATION_SECONDS } from '../src/domain/slots';

const badge: SlotDefinition = { name: 'badge', fromSeconds: 0, toSeconds: null, lastSeconds: null, x: 90, y: 300, w: 900, h: 90 };
const title: SlotDefinition = { name: 'title', fromSeconds: 0, toSeconds: 3, lastSeconds: null, x: 90, y: 420, w: 900, h: 300 };
const cta: SlotDefinition = { name: 'cta', fromSeconds: null, toSeconds: null, lastSeconds: 2, x: 90, y: 1100, w: 900, h: 120, params: ['join_host'] };

describe('slot visibility by time', () => {
  it('shows the badge for the whole short', () => {
    for (const second of [0, 1.5, 15, 29, 30]) {
      expect(isSlotVisible(badge, second), `badge at ${second} s`).toBe(true);
    }
  });

  it('shows the title only before the three-second mark', () => {
    expect(isSlotVisible(title, 0), 'title at 0 s').toBe(true);
    expect(isSlotVisible(title, 2.9), 'title at 2.9 s').toBe(true);
    expect(isSlotVisible(title, 3), 'title at 3 s').toBe(false);
    expect(isSlotVisible(title, 20), 'title at 20 s').toBe(false);
  });

  it('shows the CTA only in the last two seconds', () => {
    expect(isSlotVisible(cta, 0), 'cta at 0 s').toBe(false);
    expect(isSlotVisible(cta, 27.5), 'cta at 27.5 s').toBe(false);
    expect(isSlotVisible(cta, 28), 'cta at 28 s').toBe(true);
    expect(isSlotVisible(cta, SHORT_DURATION_SECONDS), 'cta at the end').toBe(true);
  });
});

describe('preview geometry', () => {
  const template: Template = {
    id: 'short-9x16',
    width: 1080,
    height: 1920,
    safeZone: { topPercent: 14, bottomPercent: 35 },
    slots: [badge, title, cta],
  };

  it('scales slot rectangles from design pixels', () => {
    expect(previewRect(badge, 0.4)).toEqual({ left: 36, top: 120, width: 360, height: 36 });
  });

  it('places the unsafe bands at the top 14 % and the bottom 35 % of the canvas', () => {
    const bands = safeBands(template, 0.5);

    expect(bands.top).toEqual({ top: 0, height: 134.4, percent: 14 });
    expect(bands.bottom.top).toBeCloseTo(624);
    expect(bands.bottom.height).toBeCloseTo(336);
  });
});
