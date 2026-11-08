import { ApiError, errorMessage } from '../../api/client';
import type { PublicationStatus, StepName, StepState } from './types';

export const STATUS_LABELS: Record<PublicationStatus, string> = {
  planned: 'Geplant',
  uploading: 'Wird hochgeladen',
  scheduled: 'Auf Plattform geplant',
  published: 'Veröffentlicht',
  failed: 'Fehlgeschlagen',
};

export const PLATFORM_LABELS: Record<string, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
};

export const STEP_LABELS: Record<StepName, string> = {
  upload: 'Upload',
  schedule: 'Planen',
  metadata: 'Metadaten',
  publish: 'Veröffentlicht',
  metrics: 'Metriken',
};

export const STEP_STATE_LABELS: Record<StepState, string> = {
  done: 'erledigt',
  running: 'läuft',
  waiting: 'wartet',
  failed: 'fehlgeschlagen',
};

export function statusLabel(status: PublicationStatus): string {
  return STATUS_LABELS[status];
}

export function platformLabel(platform: string): string {
  return PLATFORM_LABELS[platform] ?? platform;
}

/** The fixed text of a refused plan. The server's own detail is never shown here. */
export const CLIP_CONFLICT = 'Dieser Clip ist auf diesem Kanal bereits eingeplant.';
export const TIME_OR_CLIP_REFUSED =
  'Der Termin muss mindestens 15 Minuten in der Zukunft liegen. Eingeplant werden nur fertig gerenderte Clips.';
export const SERIES_NO_CLIP = 'Für diese Serie gibt es keinen fertig gerenderten, noch nicht eingeplanten Clip.';
export const CHANGE_REFUSED = 'Nur geplante Veröffentlichungen können geändert werden.';
export const DELETE_REFUSED = 'Nur geplante oder hochgeladene Veröffentlichungen können gelöscht werden.';

/** The message for a refused plan of one clip (context "clip") or of a series (context "series"). */
export function publicationErrorMessage(error: unknown, context: 'clip' | 'series' = 'clip'): string {
  if (error instanceof ApiError) {
    if (error.status === 409 && context === 'clip') {
      return CLIP_CONFLICT;
    }
    if (error.status === 422) {
      return context === 'series' ? SERIES_NO_CLIP : TIME_OR_CLIP_REFUSED;
    }
  }
  return errorMessage(error);
}

/** The message for a refused change or delete of an existing publication. */
export function changeErrorMessage(error: unknown, operation: 'change' | 'delete'): string {
  if (error instanceof ApiError && error.status === 409) {
    return operation === 'change' ? CHANGE_REFUSED : DELETE_REFUSED;
  }
  return errorMessage(error);
}
