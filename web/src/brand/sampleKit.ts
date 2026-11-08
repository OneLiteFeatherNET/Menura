// A small brand kit for stories and unit tests. It has the shape the API serves; the values are the first version's
// overlay roles for a few tokens, and the short 9:16 template with its slots, timing and parameters.

import type { BrandKitVersion, Template } from './brandKit';

export const sampleShort: Template = {
  format: 'short-9x16',
  name: 'Short 9:16',
  width: 1080,
  height: 1920,
  parameters: [
    { name: 'title', type: 'text', required: true },
    { name: 'subtitle', type: 'text', required: false },
    { name: 'image', type: 'image', required: false },
    { name: 'join_domain', type: 'text', required: true },
  ],
  slots: [
    { name: 'background', kind: 'shape', colour: null, background: 'surface', parameter: null, x: 0, y: 0, width: 100, height: 100, timing: { from: 0, until: null, fromEnd: false } },
    { name: 'badge', kind: 'text', colour: 'on-primary', background: 'primary', parameter: null, x: 4, y: 16, width: 30, height: 5, timing: { from: 0, until: null, fromEnd: false } },
    { name: 'logo', kind: 'image', colour: null, background: null, parameter: null, x: 80, y: 15, width: 16, height: 5, timing: { from: 0, until: null, fromEnd: false } },
    { name: 'title', kind: 'text', colour: 'on-surface', background: null, parameter: 'title', x: 4, y: 22, width: 92, height: 14, timing: { from: 0, until: 3, fromEnd: false } },
    { name: 'captions', kind: 'text', colour: 'on-surface', background: null, parameter: null, x: 4, y: 40, width: 92, height: 20, timing: { from: 0, until: null, fromEnd: false } },
    { name: 'session-tiles', kind: 'tiles', colour: null, background: null, parameter: null, x: 4, y: 66, width: 92, height: 20, timing: { from: 0, until: null, fromEnd: false } },
    { name: 'cta', kind: 'text', colour: 'on-primary', background: 'primary', parameter: 'join_domain', x: 4, y: 88, width: 92, height: 6, timing: { from: 2, until: null, fromEnd: true } },
  ],
};

export const sampleVersion: BrandKitVersion = {
  version: 1,
  publishedBy: '00000000-0000-0000-0000-000000000000',
  createdAt: '2026-11-08T10:00:00Z',
  kit: {
    colours: [
      { name: 'primary', light: '#6638e4', dark: '#ccbdff' },
      { name: 'on-primary', light: '#ffffff', dark: '#360096' },
      { name: 'surface', light: '#fffbff', dark: '#1c1b1e' },
      { name: 'on-surface', light: '#1c1b1e', dark: '#e6e1e6' },
    ],
    ramps: [{ name: 'brand', seed: '#6a3de8', tones: [{ tone: 0, hex: '#000000' }, { tone: 100, hex: '#ffffff' }] }],
    textRoles: [{ name: 'on-primary', colour: 'on-primary', background: 'primary' }],
    typography: [],
    marks: [
      {
        name: 'feather',
        variants: [
          { name: 'light', file: 'marks/feather-light.svg', safeArea: { top: 10, right: 10, bottom: 10, left: 10 } },
          { name: 'dark', file: 'marks/feather-dark.svg', safeArea: { top: 10, right: 10, bottom: 10, left: 10 } },
        ],
      },
    ],
    voice: '',
    texts: [],
    templates: [sampleShort],
  },
};
