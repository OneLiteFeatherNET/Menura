// Remembers the upload session of this browser, so that a reload finds it again. It is a convenience only: the server
// holds the session and its confirmed parts, and a session is found again by its identifier alone. Browser storage can
// be blocked or cleared, so every access is guarded and a failure only means that nothing is remembered.

export interface ResumeStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export const RESUME_KEY = 'menura.library.upload-session';

export function rememberUpload(storage: ResumeStorage, sessionId: string): void {
  try {
    storage.setItem(RESUME_KEY, sessionId);
  } catch {
    // Blocked storage: the upload still runs, it just cannot be found again after a reload.
  }
}

export function recallUpload(storage: ResumeStorage): string | null {
  try {
    return storage.getItem(RESUME_KEY);
  } catch {
    return null;
  }
}

export function forgetUpload(storage: ResumeStorage): void {
  try {
    storage.removeItem(RESUME_KEY);
  } catch {
    // Nothing to forget when storage is blocked.
  }
}

/** Whether a re-selected file is the file of the session: its composite checksum must equal the declared one. */
export function isSameUpload(declaredChecksum: string, computedChecksum: string): boolean {
  return declaredChecksum === computedChecksum;
}
