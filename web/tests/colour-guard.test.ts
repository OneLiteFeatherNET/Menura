import { describe, expect, it } from 'vitest';
import { badgeColors, isHexColor, NEUTRAL_FILL, NEUTRAL_TEXT, swatchColors } from '../src/domain/color';

describe('isHexColor', () => {
  it('accepts a six-digit hex colour in either letter case', () => {
    expect(isHexColor('#6638E4')).toBe(true);
    expect(isHexColor('#6638e4')).toBe(true);
  });

  it.each([
    ['a short form', '#FFF'],
    ['a named colour', 'red'],
    ['an eight-digit form with alpha', '#FFFFFF00'],
    ['a value with spaces around it', ' #FFFFFF'],
    ['a non-hex digit', '#GGGGGG'],
    ['a css function', 'url(javascript:alert(1))'],
    ['an empty string', ''],
  ])('rejects %s', (_label, value) => {
    expect(isHexColor(value)).toBe(false);
  });

  it('rejects values that are not strings', () => {
    expect(isHexColor(undefined)).toBe(false);
    expect(isHexColor(null)).toBe(false);
    expect(isHexColor(0xffffff)).toBe(false);
  });
});

describe('badgeColors', () => {
  it('passes valid series colours through', () => {
    expect(badgeColors('#6638E4', '#FFFFFF')).toEqual({ background: '#6638E4', color: '#FFFFFF' });
  });

  it('falls back to the neutral token for an invalid fill and keeps a valid text colour', () => {
    expect(badgeColors('url(evil)', '#000000')).toEqual({ background: NEUTRAL_FILL, color: '#000000' });
  });

  it('falls back to the neutral token for an invalid text colour', () => {
    expect(badgeColors('#6638E4', 'expression(alert(1))')).toEqual({
      background: '#6638E4',
      color: NEUTRAL_TEXT,
    });
  });
});

describe('swatchColors', () => {
  it('uses a readable text colour for a valid brand colour', () => {
    expect(swatchColors('#FFFFFF')).toEqual({ background: '#FFFFFF', color: '#000000' });
    expect(swatchColors('#000000')).toEqual({ background: '#000000', color: '#FFFFFF' });
  });

  it('renders the neutral token pair for an invalid brand colour', () => {
    expect(swatchColors('javascript:1')).toEqual({ background: NEUTRAL_FILL, color: NEUTRAL_TEXT });
  });
});
