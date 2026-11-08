// The brand kit as the API serves it (contracts/openapi/menura.yaml, schemas BrandKitVersion and BrandKit). The browser
// reads it with its session cookie and never stores it; the functions below are pure, so they run without the network.

import { apiBase, type Fetcher } from '../auth/session';

export interface ColourToken {
  name: string;
  light: string;
  dark: string;
}

export interface Tone {
  tone: number;
  hex: string;
}

export interface TonalRamp {
  name: string;
  seed: string;
  tones: Tone[];
}

export interface SafeArea {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface MarkVariant {
  name: string;
  asset: string;
  safeArea: SafeArea;
}

export interface Mark {
  name: string;
  variants: MarkVariant[];
}

export interface Timing {
  from: number;
  until: number | null;
  fromEnd: boolean;
}

export interface Slot {
  name: string;
  kind: string;
  colour: string | null;
  background: string | null;
  parameter: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
  timing: Timing;
}

export interface Parameter {
  name: string;
  type: 'text' | 'image';
  required: boolean;
}

export interface Template {
  format: string;
  name: string;
  width: number;
  height: number;
  slots: Slot[];
  parameters: Parameter[];
}

export interface BrandKit {
  colours: ColourToken[];
  ramps: TonalRamp[];
  textRoles: { name: string; colour: string; background: string }[];
  typography: { role: string; fontAsset: string; sizesPx: number[] }[];
  marks: Mark[];
  voice: string;
  texts: { language: string; endCard: string; hashtags: string[] }[];
  templates: Template[];
}

export interface BrandKitVersion {
  version: number;
  publishedBy: string;
  createdAt: string;
  kit: BrandKit;
}

/** The newest version of the kit. A failure is thrown so that the view can offer a retry. */
export async function fetchBrandKit(fetcher: Fetcher = fetch): Promise<BrandKitVersion> {
  const response = await fetcher(`${apiBase()}/brand-kit`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`The brand kit could not be read (HTTP ${response.status}).`);
  }
  return (await response.json()) as BrandKitVersion;
}

/** The CSS custom properties of the colour tokens for one scheme, as the view applies them on its root element. */
export function tokenStyle(kit: BrandKit, scheme: 'light' | 'dark'): Record<string, string> {
  const style: Record<string, string> = {};
  for (const token of kit.colours) {
    style[`--brand-${token.name}`] = scheme === 'light' ? token.light : token.dark;
  }
  return style;
}

/** The placement of a slot in percent of the frame, as CSS box values. */
export function slotBox(slot: Slot): Record<string, string> {
  return {
    left: `${slot.x}%`,
    top: `${slot.y}%`,
    width: `${slot.width}%`,
    height: `${slot.height}%`,
  };
}

/** Whether a slot is shown at this second of an output of the given duration (the timing rule of the server). */
export function isVisibleAt(timing: Timing, second: number, duration: number): boolean {
  const start = timing.fromEnd ? duration - timing.from : timing.from;
  const end = timing.until === null ? duration : timing.fromEnd ? duration - timing.until : timing.until;
  return second >= start && second < end;
}

/** Sample values for the parameters a template declares: text sample copy, and a placeholder for an image. */
export function sampleValues(template: Template): Record<string, string> {
  const values: Record<string, string> = {};
  for (const parameter of template.parameters) {
    values[parameter.name] = sampleFor(parameter.name, parameter.type);
  }
  return values;
}

/**
 * The reasons the values do not fit the template's own declarations: a value for an undeclared parameter, or no value
 * for a required one. An empty list means the values may be rendered.
 */
export function parameterProblems(template: Template, values: Record<string, string>): string[] {
  const declared = new Set(template.parameters.map((parameter) => parameter.name));
  const problems: string[] = [];
  for (const name of Object.keys(values)) {
    if (!declared.has(name)) {
      problems.push(`Der Parameter ${name} ist in dieser Vorlage nicht deklariert.`);
    }
  }
  for (const parameter of template.parameters) {
    if (parameter.required && !(values[parameter.name] ?? '').trim()) {
      problems.push(`Der Parameter ${parameter.name} fehlt.`);
    }
  }
  return problems;
}

function sampleFor(name: string, type: Parameter['type']): string {
  if (type === 'image') {
    return 'Bild';
  }
  switch (name) {
    case 'title':
      return 'Beispieltitel';
    case 'subtitle':
      return 'Untertitel';
    case 'join_domain':
      return 'join.example';
    default:
      return name;
  }
}
