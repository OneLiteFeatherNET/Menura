/** Neutral token pair used in place of an API colour that is not #RRGGBB. */
export const NEUTRAL_FILL = 'var(--md-sys-color-surface-variant)';
export const NEUTRAL_TEXT = 'var(--md-sys-color-on-surface-variant)';

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

/** True only for a six-digit hex colour such as #6638E4. Colours from API data are checked with this before any style binding. */
export function isHexColor(value: unknown): value is string {
  return typeof value === 'string' && HEX_COLOR.test(value);
}

/** Background and text colour of a series badge. Each value that is not #RRGGBB falls back to a neutral token. */
export function badgeColors(fill: unknown, text: unknown): { background: string; color: string } {
  return {
    background: isHexColor(fill) ? fill : NEUTRAL_FILL,
    color: isHexColor(text) ? text : NEUTRAL_TEXT,
  };
}

/** Background and label colour of a brand colour swatch. The label is black or white for a valid colour. */
export function swatchColors(value: unknown): { background: string; color: string } {
  return isHexColor(value)
    ? { background: value, color: readableTextOn(value) }
    : { background: NEUTRAL_FILL, color: NEUTRAL_TEXT };
}

/**
 * Black or white, whichever reads better on the given #RRGGBB background. Used for labels on swatches
 * of the brand kit, where the text colour is not part of the token.
 */
export function readableTextOn(hex: string): '#000000' | '#FFFFFF' {
  const value = hex.replace('#', '');
  const red = parseInt(value.slice(0, 2), 16);
  const green = parseInt(value.slice(2, 4), 16);
  const blue = parseInt(value.slice(4, 6), 16);
  const brightness = (red * 299 + green * 587 + blue * 114) / 1000;
  return brightness > 150 ? '#000000' : '#FFFFFF';
}
