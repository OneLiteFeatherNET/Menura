// What the editor declares before an upload starts, checked in the browser with the same rules the server applies
// (contracts/openapi/menura.yaml, AssetUploadRequest). The server checks again; these messages only keep an invalid
// declaration from being sent. Duration and resolution are read from the file where the browser can, and they stay
// editable, because the server never probes the media.

export type UploadMode = 'raw' | 'finished';
export type AssetKind = 'video' | 'image';
export type UploadKind = 'raw_material' | AssetKind;

export const VIDEO_FORMATS: readonly string[] = ['mp4', 'mov', 'webm', 'mkv'];
export const IMAGE_FORMATS: readonly string[] = ['png', 'jpg', 'jpeg', 'webp', 'gif'];
const MAX_DIMENSION = 16384;
const MAX_TITLE_LENGTH = 200;

/** The raw input of the form, as the editor typed it. Numbers arrive as text. */
export interface DeclarationInput {
  mode: UploadMode;
  fileName: string;
  sizeBytes: number;
  durationSeconds: string;
  width: string;
  height: string;
  streamTitle: string;
  streamDate: string;
}

/** The checked values, ready for the request body without the checksum. */
export interface DeclaredFields {
  kind: UploadKind;
  format: string;
  durationSeconds: number | null;
  width: number;
  height: number;
  sizeBytes: number;
  streamTitle: string | null;
  streamDate: string | null;
}

export type DeclarationField = 'format' | 'durationSeconds' | 'width' | 'height' | 'streamTitle' | 'streamDate';

export type DeclarationCheck =
  | { ok: true; fields: DeclaredFields }
  | { ok: false; errors: Partial<Record<DeclarationField, string>> };

/** The lower-case extension of a file name, or an empty text when there is none. */
export function formatOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase();
}

/** Whether a format is a video container or an image format; null for anything else. */
export function kindForFormat(format: string): AssetKind | null {
  if (VIDEO_FORMATS.includes(format)) return 'video';
  if (IMAGE_FORMATS.includes(format)) return 'image';
  return null;
}

export function checkDeclaration(input: DeclarationInput): DeclarationCheck {
  const errors: Partial<Record<DeclarationField, string>> = {};
  const format = formatOf(input.fileName);
  const assetKind = kindForFormat(format);
  const kind: UploadKind | null = input.mode === 'raw' ? 'raw_material' : assetKind;

  if (input.mode === 'raw' && assetKind !== 'video') {
    errors.format = 'Rohmaterial ist eine Videodatei (MP4, MOV, WebM oder MKV).';
  } else if (kind === null) {
    errors.format = 'Dateityp nicht unterstützt. Erlaubt sind MP4, MOV, WebM, MKV, PNG, JPG, WebP und GIF.';
  }

  const duration = parsePositiveInteger(input.durationSeconds);
  const needsDuration = kind === 'raw_material' || kind === 'video';
  if (needsDuration && duration === null) {
    errors.durationSeconds = 'Bitte die Länge in Sekunden angeben (ganze Zahl, mindestens 1).';
  }

  const width = parseDimension(input.width);
  if (width === null) errors.width = 'Bitte die Breite in Pixeln angeben (1 bis 16384).';
  const height = parseDimension(input.height);
  if (height === null) errors.height = 'Bitte die Höhe in Pixeln angeben (1 bis 16384).';

  let streamTitle: string | null = null;
  let streamDate: string | null = null;
  if (kind === 'raw_material') {
    streamTitle = checkTitle(input.streamTitle);
    if (streamTitle === null) {
      errors.streamTitle = `Bitte den Titel des Streams angeben (höchstens ${MAX_TITLE_LENGTH} Zeichen, ohne Steuerzeichen).`;
    }
    streamDate = isRealDate(input.streamDate) ? input.streamDate : null;
    if (streamDate === null) errors.streamDate = 'Bitte das Datum des Streams angeben.';
  }

  if (Object.keys(errors).length > 0 || kind === null || width === null || height === null) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    fields: {
      kind,
      format,
      durationSeconds: needsDuration ? duration : null,
      width,
      height,
      sizeBytes: input.sizeBytes,
      streamTitle,
      streamDate,
    },
  };
}

/** The request body of POST /asset-upload-sessions (AssetUploadRequest). */
export interface UploadRequestBody {
  kind: UploadKind;
  format: string;
  durationSeconds?: number;
  width: number;
  height: number;
  sizeBytes: number;
  checksum: string;
  streamTitle?: string;
  streamDate?: string;
}

/** The body with the checksum. Duration and stream fields are left out when they do not apply. */
export function toUploadRequest(fields: DeclaredFields, checksum: string): UploadRequestBody {
  const body: UploadRequestBody = {
    kind: fields.kind,
    format: fields.format,
    width: fields.width,
    height: fields.height,
    sizeBytes: fields.sizeBytes,
    checksum,
  };
  if (fields.durationSeconds !== null) body.durationSeconds = fields.durationSeconds;
  if (fields.streamTitle !== null) body.streamTitle = fields.streamTitle;
  if (fields.streamDate !== null) body.streamDate = fields.streamDate;
  return body;
}

function parsePositiveInteger(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value >= 1 ? value : null;
}

function parseDimension(text: string): number | null {
  const value = parsePositiveInteger(text);
  return value !== null && value <= MAX_DIMENSION ? value : null;
}

function checkTitle(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_TITLE_LENGTH) return null;
  // Control and format characters are refused, as the server refuses them.
  if (/[\p{Cc}\p{Cf}]/u.test(trimmed)) return null;
  return trimmed;
}

function isRealDate(text: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return false;
  const [year, month, day] = text.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}
