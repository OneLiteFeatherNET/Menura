import type { SlotDefinition, Template } from '../api/types';

/** The preview plays a 30 s short. */
export const SHORT_DURATION_SECONDS = 30;

/** Canvas size of the short template in design pixels (1080 x 1920, 9:16). */
export const CANVAS = { width: 1080, height: 1920 } as const;

export const SLOT_LABELS: Record<string, string> = {
  badge: 'Badge',
  title: 'Titel',
  captions: 'Untertitel',
  'session-tiles': 'Session-Kacheln',
  logo: 'Logo',
  cta: 'Join-CTA',
};

/**
 * Whether a slot is on screen at a given second of the short.
 * - `lastSeconds` set: visible only in the last N seconds (the CTA).
 * - otherwise visible from `fromSeconds` (default 0) until `toSeconds` (default: to the end),
 *   with the end exclusive: a title with toSeconds 3 is gone at second 3.
 */
export function isSlotVisible(
  slot: SlotDefinition,
  atSecond: number,
  durationSeconds: number = SHORT_DURATION_SECONDS,
): boolean {
  if (slot.lastSeconds !== null) {
    return atSecond >= durationSeconds - slot.lastSeconds;
  }
  const from = slot.fromSeconds ?? 0;
  const beforeEnd = slot.toSeconds === null || atSecond < slot.toSeconds;
  return atSecond >= from && beforeEnd;
}

export interface PreviewRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** A slot rectangle scaled from design pixels to preview pixels. */
export function previewRect(slot: SlotDefinition, scale: number): PreviewRect {
  return {
    left: slot.x * scale,
    top: slot.y * scale,
    width: slot.w * scale,
    height: slot.h * scale,
  };
}

export interface SafeBand {
  top: number;
  height: number;
  percent: number;
}

/**
 * The two unsafe bands of a template: the top band (topPercent of the height) and the bottom
 * band (bottomPercent of the height), in preview pixels.
 */
export function safeBands(template: Template, scale: number): { top: SafeBand; bottom: SafeBand } {
  const height = template.height * scale;
  const topPercent = template.safeZone.topPercent;
  const bottomPercent = template.safeZone.bottomPercent;
  return {
    top: { top: 0, height: (height * topPercent) / 100, percent: topPercent },
    bottom: {
      top: height - (height * bottomPercent) / 100,
      height: (height * bottomPercent) / 100,
      percent: bottomPercent,
    },
  };
}
