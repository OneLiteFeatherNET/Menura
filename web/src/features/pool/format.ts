import type { AssetKind, AssetStage, TranscriptState } from './types';

const createdFormat = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Berlin',
});

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

/** Length of a recording as h:mm:ss, the hours never rolling over into days. */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${hours}:${pad(minutes)}:${pad(seconds % 60)}`;
}

/** Position of a transcript line: mm:ss below one hour, h:mm:ss from one hour on. */
export function formatTimestamp(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  if (seconds >= 3600) {
    return formatDuration(seconds);
  }
  return `${pad(Math.floor(seconds / 60))}:${pad(seconds % 60)}`;
}

/** Creation time in Europe/Berlin, whatever zone the browser runs in. */
export function formatCreatedAt(iso: string): string {
  return createdFormat.format(new Date(iso));
}

const KIND_LABELS: Record<AssetKind, string> = {
  raw: 'Rohmaterial',
  clip: 'Clip',
};

const STAGE_LABELS: Record<AssetStage, string> = {
  raw: 'Roh',
  cut: 'Geschnitten',
  rendered: 'Fertig gerendert',
};

const TRANSCRIPT_LABELS: Record<TranscriptState, string> = {
  ready: 'Transkript bereit',
  pending: 'wird erstellt',
  none: 'kein Transkript',
};

export function kindLabel(kind: AssetKind): string {
  return KIND_LABELS[kind];
}

export function stageLabel(stage: AssetStage): string {
  return STAGE_LABELS[stage];
}

export function transcriptLabel(state: TranscriptState): string {
  return TRANSCRIPT_LABELS[state];
}
