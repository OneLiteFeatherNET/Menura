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
