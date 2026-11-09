import { describe, expect, it } from 'vitest';
import {
  declarationError,
  problemText,
  quotaLine,
  resetLabel,
  visibilityMismatchText,
} from '../src/calendar/model';

describe('YouTube declaration and dry-run texts', () => {
  it('gives every YouTube dry-run code a German text, never the server message', () => {
    const codes = ['visibility_undeclared', 'made_for_kids_missing', 'short_not_vertical', 'short_too_long', 'short_unverified'];

    for (const code of codes) {
      const text = problemText({ code, severity: 'error', message: 'English server text', action: null });
      expect(text, `the text of ${code}`).not.toBe('English server text');
      expect(text, `the text of ${code} is German`).toMatch(/Sie|Die|Der|Das|Ein|Eine|Kein|Keine|muss|darf|ist/);
    }
  });

  it('keeps the server message of a code the page does not know', () => {
    expect(problemText({ code: 'unknown_code', severity: 'warning', message: 'Server text', action: null })).toBe('Server text');
  });

  it('asks for a visibility and a made-for-kids answer before the declaration is saved', () => {
    expect(declarationError({ visibility: '', madeForKids: null, plannedShort: false })).toBe('Wählen Sie die Sichtbarkeit.');
    expect(declarationError({ visibility: 'public', madeForKids: null, plannedShort: false })).toBe(
      'Beantworten Sie, ob das Video für Kinder bestimmt ist.',
    );
    expect(declarationError({ visibility: 'public', madeForKids: false, plannedShort: true })).toBeNull();
  });

  it('names the applied visibility when the platform applied another one', () => {
    expect(visibilityMismatchText({ visibility: 'public', appliedVisibility: 'private' })).toBe(
      'Angewendet: Privat, gewählt: Öffentlich.',
    );
  });

  it('shows no mismatch while the applied visibility is unknown or equal', () => {
    expect(visibilityMismatchText({ visibility: 'public', appliedVisibility: null })).toBeNull();
    expect(visibilityMismatchText({ visibility: 'public', appliedVisibility: 'public' })).toBeNull();
  });

  it('reads the remaining quota as units and uploads of the day', () => {
    expect(quotaLine({ unitsPerDay: 10000, unitsRemaining: 9800, uploadsPerDay: 6, uploadsRemaining: 5 })).toBe(
      '9800 von 10000 Einheiten · 5 von 6 Uploads frei',
    );
  });

  it('shows the reset of the quota in the installation zone of the API', () => {
    expect(resetLabel('2026-11-09T08:00:00Z', 'Europe/Berlin')).toBe('09.11.2026, 09:00');
  });
});
