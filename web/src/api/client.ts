// The API client sends every request with the session cookie and reads the RFC 9457 problem of a failed request. It
// never receives or stores a token: the server does not return one.
import { apiBase, type Fetcher } from '../auth/session';

/** A failed request. The message is the server's detail (English, for logs); the UI maps the status to German text. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;

  constructor(status: number, title: string, detail: string) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
}

/** Sends a request and returns the parsed JSON body, or undefined for 204. A failed status throws an ApiError. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}, fetcher: Fetcher = fetch): Promise<T | undefined> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method: options.method ?? 'GET', credentials: 'include', headers };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(options.body);
  }
  const response = await fetcher(`${apiBase()}${path}`, init);
  if (!response.ok) {
    throw await problemOf(response);
  }
  if (response.status === 204) {
    return undefined;
  }
  return (await response.json()) as T;
}

async function problemOf(response: Response): Promise<ApiError> {
  let title = response.statusText || `HTTP ${response.status}`;
  let detail = title;
  try {
    const body = (await response.json()) as { title?: unknown; detail?: unknown };
    if (typeof body.title === 'string') title = body.title;
    if (typeof body.detail === 'string') detail = body.detail;
  } catch {
    // A failure without a problem body keeps the status text.
  }
  return new ApiError(response.status, title, detail);
}
