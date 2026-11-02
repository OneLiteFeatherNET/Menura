import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { argbFromHex, hexFromArgb, themeFromSourceColor } from '@material/material-color-utilities';
import seed from '../src/theme/seed.json';
import { generateThemeCss, schemeTokens } from '../src/theme/generate';

const OTHER_SEED = '#00797F';
// The committed stylesheet the app imports. Vitest blanks CSS imports, so it is read as text; pnpm test runs in web/.
const tokensCss = readFileSync(resolve(process.cwd(), 'src/theme/tokens.css'), 'utf8');

describe('theme seed file', () => {
  it('holds exactly one six-digit hex seed colour', () => {
    expect(Object.keys(seed)).toEqual(['seedColor']);
    expect(seed.seedColor).toMatch(/^#[0-9A-F]{6}$/i);
  });
});

describe('theme tokens', () => {
  it('derive the light primary token from the seed colour', () => {
    const expected = hexFromArgb(themeFromSourceColor(argbFromHex(seed.seedColor)).schemes.light.primary);

    expect(schemeTokens(seed.seedColor, 'light').primary).toBe(expected);
  });

  it('change when the seed colour changes', () => {
    expect(generateThemeCss(OTHER_SEED)).not.toBe(generateThemeCss(seed.seedColor));
  });

  it('are committed exactly as the generator writes them for the seed file', () => {
    expect(tokensCss).toBe(generateThemeCss(seed.seedColor));
  });

  it('declare a dark scheme for the system preference and for data-theme dark', () => {
    expect(tokensCss).toContain('@media (prefers-color-scheme: dark)');
    expect(tokensCss).toContain(':root[data-theme="dark"]');
  });

  it('name every colour role with the Material Web sys-color prefix', () => {
    expect(tokensCss).toMatch(/--md-sys-color-primary: #[0-9a-f]{6};/i);
    expect(tokensCss).toMatch(/--md-sys-color-on-surface: #[0-9a-f]{6};/i);
  });
});
