import type { ComputedRef, InjectionKey } from 'vue';
import type { FetchLike } from '../api/client';
import type { Session } from '../api/types';

export type SessionState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'forbidden' }
  | { status: 'error'; message: string }
  | { status: 'signed-in'; session: Session };

/** The signed-in session, or null while the gate is not passed. Provided by App.vue. */
export const SESSION_KEY: InjectionKey<ComputedRef<Session | null>> = Symbol('menura-session');

const defaultFetch: FetchLike = (input, init) => fetch(input, init);

/**
 * Asks the server who is signed in: 401 is anonymous, 403 (or no roles) is signed in without a
 * Menura role, 200 is the session. Anything else is an error the gate shows.
 */
export async function loadSession(fetcher: FetchLike = defaultFetch): Promise<SessionState> {
  let response: Response;
  try {
    response = await fetcher('/auth/session', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
  } catch {
    return { status: 'error', message: 'Der Server ist gerade nicht erreichbar.' };
  }

  if (response.status === 401) {
    return { status: 'anonymous' };
  }
  if (response.status === 403) {
    return { status: 'forbidden' };
  }
  if (!response.ok) {
    return {
      status: 'error',
      message: `Die Anmeldung konnte nicht geprüft werden (HTTP ${response.status}).`,
    };
  }

  let session: Session;
  try {
    session = (await response.json()) as Session;
  } catch {
    return { status: 'error', message: 'Die Antwort des Servers ist ungültig.' };
  }
  if (session.roles.length === 0) {
    return { status: 'forbidden' };
  }
  return { status: 'signed-in', session };
}

/** Editors and admins may create series and assign posts; viewers read only. */
export function canEditSeries(roles: readonly string[]): boolean {
  return roles.includes('EDITOR') || roles.includes('ADMIN');
}
