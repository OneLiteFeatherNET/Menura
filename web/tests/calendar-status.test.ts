import { describe, expect, it } from 'vitest';
import { platformLabel, publicationErrorMessage, statusLabel } from '../src/features/calendar/labels';
import { ApiError } from '../src/api/client';
import type { ProblemDetails } from '../src/api/types';

function refusal(status: number, detail?: string): ApiError {
  const problem: ProblemDetails = { status, title: 'Problem', detail };
  return new ApiError(status, problem);
}

describe('status labels', () => {
  it.each([
    ['planned', 'Geplant'],
    ['uploading', 'Wird hochgeladen'],
    ['scheduled', 'Auf Plattform geplant'],
    ['published', 'Veröffentlicht'],
    ['failed', 'Fehlgeschlagen'],
  ] as const)('labels %s as %s', (status, label) => {
    expect(statusLabel(status)).toBe(label);
  });

  it.each([
    ['youtube', 'YouTube'],
    ['tiktok', 'TikTok'],
    ['instagram', 'Instagram'],
  ])('names the platform %s as %s', (platform, label) => {
    expect(platformLabel(platform)).toBe(label);
  });
});

describe('error texts of the plan dialog', () => {
  it('shows the fixed text for a clip already planned on the channel', () => {
    expect(publicationErrorMessage(refusal(409, 'anything the server says'))).toBe(
      'Dieser Clip ist auf diesem Kanal bereits eingeplant.',
    );
  });

  it('shows the fixed text for a refused time or clip, not the server detail', () => {
    expect(publicationErrorMessage(refusal(422, 'publishAt: mindestens 15 Minuten in der Zukunft.'))).toBe(
      'Der Termin muss mindestens 15 Minuten in der Zukunft liegen. Eingeplant werden nur fertig gerenderte Clips.',
    );
  });
});
