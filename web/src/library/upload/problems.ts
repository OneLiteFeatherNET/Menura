// The Problem Details of the API (RFC 9457) as the editor reads them: a German sentence per problem, and the line
// of a transcript that cannot be parsed. The server's own detail text is English and is kept for the record only.

import { transcriptErrorLine } from './transcript';

export interface ProblemView {
  status: number;
  title: string;
  detail: string;
}

/** A failed request. The message is the German sentence for the problem. */
export class ApiProblem extends Error {
  readonly problem: ProblemView;

  constructor(problem: ProblemView) {
    super(messageFor(problem));
    this.name = 'ApiProblem';
    this.problem = problem;
  }
}

/** The problem of a response body; a body that is not a problem falls back to the status alone. */
export function problemFromBody(body: unknown, status: number): ProblemView {
  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    return {
      status,
      title: typeof record.title === 'string' ? record.title : '',
      detail: typeof record.detail === 'string' ? record.detail : '',
    };
  }
  return { status, title: '', detail: '' };
}

/** The failure of a response that is not ok, read from its body when it has one. */
export async function failureOf(response: Response): Promise<ApiProblem> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  return new ApiProblem(problemFromBody(body, response.status));
}

/** The sentence the editor sees for a problem. */
export function messageFor(problem: ProblemView): string {
  const { status, title, detail } = problem;
  if (title === 'Invalid transcript' || detail.startsWith('At line')) {
    const line = transcriptErrorLine(detail);
    if (line !== null) {
      return `Zeile ${line} der Datei konnte nicht gelesen werden. Bitte die Zeitangabe oder den Text dieser Zeile prüfen.`;
    }
    return 'Die Datei enthält keine Untertitel mit Zeitangabe.';
  }
  switch (status) {
    case 400:
      return 'Die Angaben zur Datei sind unvollständig oder ungültig.';
    case 401:
      return 'Bitte melden Sie sich erneut an.';
    case 403:
      return 'Nur die Redaktion darf Material hochladen.';
    case 404:
      return 'Diese Übertragung oder dieser Eintrag existiert nicht mehr.';
    case 409:
      return title === 'Upload Incomplete'
        ? 'Noch nicht alle Teile sind im Speicher. Die Übertragung wird fortgesetzt.'
        : 'Der Zustand der Übertragung hat sich geändert. Bitte die Ansicht aktualisieren.';
    case 413:
      return title === 'Transcript Too Large'
        ? 'Das Transkript ist zu groß (höchstens 10 MB).'
        : 'Das Material ist größer als das erlaubte Limit.';
    case 422:
      return 'Die gespeicherte Datei stimmt nicht mit der angegebenen überein. Der Upload wurde verworfen.';
    case 503:
      return 'Der Speicher ist gerade nicht erreichbar. Die Übertragung wird fortgesetzt, sobald er antwortet.';
    default:
      return `Die Anfrage ist fehlgeschlagen (HTTP ${status}).`;
  }
}
