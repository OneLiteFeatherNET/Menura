import { argbFromHex, hexFromArgb, themeFromSourceColor } from '@material/material-color-utilities';

export type ThemeMode = 'light' | 'dark';

const TOKEN_PREFIX = '--md-sys-color-';

// Material Web reads its colour roles from --md-sys-color-*. The generator derives the full role set
// (primary, on-primary, surface-container-high, ...) from one seed colour.
export function schemeTokens(seedHex: string, mode: ThemeMode): Record<string, string> {
  const theme = themeFromSourceColor(argbFromHex(seedHex));
  const scheme = mode === 'light' ? theme.schemes.light : theme.schemes.dark;
  const tokens: Record<string, string> = {};

  for (const [role, argb] of Object.entries(scheme.toJSON())) {
    tokens[toKebab(role)] = hexFromArgb(argb);
  }
  return tokens;
}

export function generateThemeCss(seedHex: string): string {
  const light = declarations(schemeTokens(seedHex, 'light'));
  const dark = declarations(schemeTokens(seedHex, 'dark'));

  return [
    '/* Generated from src/theme/seed.json by scripts/generate-theme.ts. Do not edit by hand. */',
    ':root, :root[data-theme="light"] {',
    light,
    '}',
    '@media (prefers-color-scheme: dark) {',
    '  :root:not([data-theme="light"]) {',
    indent(dark, '  '),
    '  }',
    '}',
    ':root[data-theme="dark"] {',
    dark,
    '}',
    '',
  ].join('\n');
}

function declarations(tokens: Record<string, string>): string {
  return Object.entries(tokens)
    .map(([role, hex]) => `  ${TOKEN_PREFIX}${role}: ${hex};`)
    .join('\n');
}

function indent(block: string, prefix: string): string {
  return block
    .split('\n')
    .map((line) => `${prefix}${line}`)
    .join('\n');
}

function toKebab(camel: string): string {
  return camel.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}
