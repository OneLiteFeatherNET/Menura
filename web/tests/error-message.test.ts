import { describe, expect, it } from 'vitest';
import { ApiError, errorMessage } from '../src/api/client';

const RAW_SERVER_TEXT = 'duplicate key value violates unique constraint "series_pkey"';

function fail(status: number, detail?: string, title?: string): ApiError {
  return new ApiError(status, { status, detail, title });
}

describe('errorMessage maps status codes to fixed German messages', () => {
  it.each([
    [400, 'Eingabe ungültig – bitte Felder prüfen'],
    [422, 'Eingabe ungültig – bitte Felder prüfen'],
    [401, 'Bitte erneut anmelden'],
    [403, 'Dafür fehlt dir die Rolle'],
    [404, 'Nicht gefunden'],
    [413, 'Eingabe zu groß'],
    [500, 'Serverfehler – bitte später erneut'],
    [503, 'Serverfehler – bitte später erneut'],
  ])('shows the fixed message for status %i', (status, expected) => {
    expect(errorMessage(fail(status))).toBe(expected);
  });

  it('shows a generic message for another 4xx status', () => {
    expect(errorMessage(fail(409))).toBe('Die Anfrage wurde abgelehnt.');
  });

  it('never shows the raw detail or title of the server', () => {
    expect(errorMessage(fail(400, RAW_SERVER_TEXT, 'Bad Request'))).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
    expect(errorMessage(fail(500, RAW_SERVER_TEXT, 'Internal Server Error'))).toBe(
      'Serverfehler – bitte später erneut',
    );
  });

  it('does not show server detail on 422 unless the caller allows it', () => {
    expect(errorMessage(fail(422, 'Hashtag ist ungültig'))).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
  });
});

describe('errorMessage with the 422 server detail allowed (new-series dialog)', () => {
  const allow = { serverDetailOn422: true };

  it('shows a short printable detail', () => {
    expect(errorMessage(fail(422, 'Hashtag ist ungültig'), allow)).toBe('Hashtag ist ungültig');
  });

  it('shows the fixed message when the detail is longer than 120 characters', () => {
    expect(errorMessage(fail(422, 'x'.repeat(121)), allow)).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
  });

  it('accepts a detail of exactly 120 characters', () => {
    expect(errorMessage(fail(422, 'x'.repeat(120)), allow)).toBe('x'.repeat(120));
  });

  it('strips control characters before showing the detail', () => {
    expect(errorMessage(fail(422, 'Name\u0007 ist\u0000 leer\n'), allow)).toBe('Name ist leer');
  });

  it('falls back to the fixed message when nothing printable remains', () => {
    expect(errorMessage(fail(422, '\u0001\u0002'), allow)).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
  });

  it('shows the fixed message when the server sent no detail', () => {
    expect(errorMessage(fail(422, undefined, 'Unprocessable Entity'), allow)).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
  });

  it('does not allow the detail for other statuses', () => {
    expect(errorMessage(fail(400, 'Hashtag ist ungültig'), allow)).toBe(
      'Eingabe ungültig – bitte Felder prüfen',
    );
    expect(errorMessage(fail(500, 'Hashtag ist ungültig'), allow)).toBe(
      'Serverfehler – bitte später erneut',
    );
  });
});

describe('errorMessage for errors that are not API answers', () => {
  it('reports an unreachable server for a network failure', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe(
      'Der Server ist gerade nicht erreichbar.',
    );
  });

  it('reports an unexpected error for anything else', () => {
    expect(errorMessage(new Error(RAW_SERVER_TEXT))).toBe('Unerwarteter Fehler.');
  });
});
