// The state of one upload as the dialog shows it. Pure: events in, a new state out, so every phase can be tested
// without a network or a file.

export type UploadPhase =
  | 'idle'
  | 'hashing'
  | 'flagged'
  | 'uploading'
  | 'completing'
  | 'completed'
  | 'failed'
  | 'cancelled';

export interface UploadState {
  phase: UploadPhase;
  fileName: string;
  partCount: number;
  hashedParts: number;
  confirmedParts: number[];
  failedParts: number[];
  duplicateOf: string | null;
  sessionId: string | null;
  assetId: string | null;
  message: string | null;
}

export const IDLE_UPLOAD: UploadState = {
  phase: 'idle',
  fileName: '',
  partCount: 0,
  hashedParts: 0,
  confirmedParts: [],
  failedParts: [],
  duplicateOf: null,
  sessionId: null,
  assetId: null,
  message: null,
};

export type UploadEvent =
  | { type: 'selected'; fileName: string; partCount: number }
  | { type: 'hashed'; done: number; total: number }
  | { type: 'sessionStarted'; sessionId: string; flagged: boolean; duplicateOf: string | null; confirmedParts: number[] }
  | { type: 'duplicateConfirmed' }
  | { type: 'partConfirmed'; partNumber: number }
  | { type: 'partsFailed'; parts: number[]; message: string }
  | { type: 'completing' }
  | { type: 'completed'; assetId: string }
  | { type: 'failed'; message: string }
  | { type: 'cancelled' };

export function reduceUpload(state: UploadState, event: UploadEvent): UploadState {
  switch (event.type) {
    case 'selected':
      return { ...IDLE_UPLOAD, phase: 'hashing', fileName: event.fileName, partCount: event.partCount };
    case 'hashed':
      return { ...state, hashedParts: event.done };
    case 'sessionStarted':
      return {
        ...state,
        phase: event.flagged ? 'flagged' : 'uploading',
        sessionId: event.sessionId,
        duplicateOf: event.duplicateOf,
        confirmedParts: sortedUnique(event.confirmedParts),
        failedParts: [],
        message: null,
      };
    case 'duplicateConfirmed':
      return { ...state, phase: 'uploading', duplicateOf: null };
    case 'partConfirmed':
      return { ...state, confirmedParts: sortedUnique([...state.confirmedParts, event.partNumber]), failedParts: [] };
    case 'partsFailed':
      return { ...state, phase: 'failed', failedParts: sortedUnique(event.parts), message: event.message };
    case 'completing':
      return { ...state, phase: 'completing', failedParts: [], message: null };
    case 'completed':
      return { ...state, phase: 'completed', assetId: event.assetId, failedParts: [], message: null };
    case 'failed':
      return { ...state, phase: 'failed', message: event.message };
    case 'cancelled':
      return { ...state, phase: 'cancelled', failedParts: [], message: null };
  }
}

/** The progress of the phase in whole percent: hashing counts hashed parts, uploading counts confirmed parts. */
export function percentOf(state: UploadState): number {
  if (state.phase === 'completed') return 100;
  if (state.partCount === 0) return 0;
  const done = state.phase === 'hashing' ? state.hashedParts : state.confirmedParts.length;
  return Math.round((done / state.partCount) * 100);
}

function sortedUnique(values: readonly number[]): number[] {
  return [...new Set(values)].sort((a, b) => a - b);
}
