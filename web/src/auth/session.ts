// The web app is a backend-for-frontend: the browser holds only the __Host- session cookie, which the API sends with
// credentials. Nothing here reads or stores a token.

export interface SessionInfo {
  subject: string;
  roles: string[];
}

export type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

const DEFAULT_API = 'https://api-menura.onelitefeather.net';

/** The API origin: the production host, or the value of VITE_API_BASE at build time (the e2e build sets it). */
export function apiBase(): string {
  return (import.meta.env.VITE_API_BASE as string | undefined) || DEFAULT_API;
}

/** Where a visitor starts a sign-in; the API redirects to Entra ID through Menura's authorization server. */
export function signInUrl(): string {
  return `${apiBase()}/auth/login`;
}

/** The signed-in person, or null for an anonymous visitor. Any other failure is thrown. */
export async function fetchSession(fetcher: Fetcher = fetch): Promise<SessionInfo | null> {
  const response = await fetcher(`${apiBase()}/auth/session`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`The session could not be read (HTTP ${response.status}).`);
  }
  return (await response.json()) as SessionInfo;
}

/** Ends the session on the server and clears the cookie in the browser. */
export async function signOut(fetcher: Fetcher = fetch): Promise<void> {
  await fetcher(`${apiBase()}/auth/logout`, { method: 'POST', credentials: 'include' });
}

const ROLE_LABELS: Record<string, string> = {
  VIEWER: 'Betrachter',
  EDITOR: 'Redaktion',
  PUBLISHER: 'Veröffentlichung',
  OPERATOR: 'Betrieb',
  ADMIN: 'Administration',
};

/** The German name of a role as the server names it. */
export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role;
}
