/** Geometry and colour rules of the inline SVG charts. Pure, so the rules are tested without a DOM. */

export interface Box {
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  bottom: number;
}

export interface Point {
  x: number;
  y: number;
}

/** A tidy upper bound for the y axis of counts, so 0 is the baseline and the top is a round number. */
export function axisMax(values: number[]): number {
  const max = Math.max(0, ...values);
  if (max === 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(max));
  return Math.ceil(max / magnitude) * magnitude;
}

/** Evenly spaced points for a line; a null value leaves a gap and yields no point. */
export function linePoints(values: (number | null)[], max: number, box: Box): (Point | null)[] {
  const plotWidth = box.width - box.left - box.right;
  const plotHeight = box.height - box.top - box.bottom;
  const step = values.length <= 1 ? 0 : plotWidth / (values.length - 1);
  return values.map((value, index) => {
    if (value === null) {
      return null;
    }
    return {
      x: box.left + (values.length === 1 ? plotWidth / 2 : index * step),
      y: box.top + plotHeight - (value / max) * plotHeight,
    };
  });
}

/** The SVG path through the points; gaps (null) start a new sub-path. */
export function linePath(points: (Point | null)[]): string {
  let path = '';
  let pen = 'M';
  for (const point of points) {
    if (point === null) {
      pen = 'M';
      continue;
    }
    path += `${pen}${round(point.x)} ${round(point.y)} `;
    pen = 'L';
  }
  return path.trim();
}

/** The horizontal extent of one category in a grouped bar chart. */
export function categoryWidth(count: number, box: Box): number {
  return count === 0 ? 0 : (box.width - box.left - box.right) / count;
}

/** Heat levels of a sequential scale: 0 is no posts, 1 to 4 are rising intensity. */
export function heatLevel(value: number | null, max: number): number {
  if (value === null || max <= 0) {
    return 0;
  }
  return Math.min(4, Math.max(1, Math.ceil((value / max) * 4)));
}

const HEAT_SHARE = [25, 50, 75, 100];

/**
 * The fill of a heat cell, mixed from the primary theme role. Level 0 uses the neutral container role.
 * Only theme tokens are used, so the scale follows light and dark themes.
 */
export function heatBackground(level: number): string {
  if (level <= 0) {
    return 'var(--md-sys-color-surface-container)';
  }
  const share = HEAT_SHARE[Math.min(level, 4) - 1];
  return `color-mix(in srgb, var(--md-sys-color-primary) ${share}%, var(--md-sys-color-surface-container-lowest))`;
}

/** Bar series colours in order; the theme roles keep the palette consistent with the rest of the app. */
export const SERIES_COLORS = [
  'var(--md-sys-color-primary)',
  'var(--md-sys-color-tertiary)',
  'var(--md-sys-color-secondary)',
];

function round(value: number): number {
  return Math.round(value * 10) / 10;
}
