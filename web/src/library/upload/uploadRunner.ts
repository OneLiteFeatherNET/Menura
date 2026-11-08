// The sequence of one upload: hash the file, start the session, send the missing parts with bounded concurrency,
// complete. Every step reports to a dispatch function with a UploadEvent, so the dialog only shows what happened. The
// API and the file are injected, so the sequence runs against fakes in the unit tests.

import type { PartUrl, UploadSessionView } from '../../api/libraryUploads';
import { compositeChecksum, hashParts, toBase64 } from './checksum';
import type { DeclaredFields, UploadRequestBody } from './declaration';
import { toUploadRequest } from './declaration';
import { isAbortError, runBounded } from './concurrency';
import { PART_SIZE_BYTES, planParts, type PartPlan } from './partPlan';
import { messageFor, type ProblemView } from './problems';
import { isSameUpload } from './resume';
import type { UploadEvent } from './uploadState';

/** The file as the runner reads it: slices for hashing and blobs for the part requests. */
export interface UploadSource {
  readonly sizeBytes: number;
  bytes(start: number, end: number): Promise<Uint8Array>;
  blob(start: number, end: number): Blob;
}

/** The calls of the upload, as the API module provides them. */
export interface UploadApi {
  start(body: UploadRequestBody): Promise<UploadSessionView>;
  read(id: string): Promise<UploadSessionView>;
  confirmDuplicate(id: string): Promise<UploadSessionView>;
  complete(id: string): Promise<UploadSessionView>;
  cancel(id: string): Promise<UploadSessionView>;
  putPart(url: string, body: Blob, sha256Base64: string, signal?: AbortSignal): Promise<void>;
}

export interface Prepared {
  plan: PartPlan;
  digests: Uint8Array[];
  checksum: string;
}

export type Dispatch = (event: UploadEvent) => void;

/** How often a part is requested again, with fresh URLs, before the upload reports it as failed. */
export const MAX_ROUNDS = 3;
/** Parts in flight at once (the server and the storage allow more; three keeps a slow uplink responsive). */
export const CONCURRENCY = 3;

/** Hashes every part of the file and derives the composite checksum the server expects. */
export async function prepareUpload(
  source: UploadSource,
  partSizeBytes: number = PART_SIZE_BYTES,
  onHashed?: (done: number, total: number) => void,
): Promise<Prepared> {
  const plan = planParts(source.sizeBytes, partSizeBytes);
  const digests = await hashParts(plan, (start, end) => source.bytes(start, end), onHashed);
  return { plan, digests, checksum: await compositeChecksum(digests) };
}

export interface TransferOptions {
  concurrency?: number;
  signal?: AbortSignal;
  onConfirmed?: (partNumber: number) => void;
}

/**
 * Sends the given parts. A part the storage refuses is requested again after a fresh read of the session, which
 * returns new URLs for the parts still missing. The result names the parts that failed in every round.
 */
export async function transferParts(
  api: Pick<UploadApi, 'read' | 'putPart'>,
  sessionId: string,
  initial: readonly PartUrl[],
  prepared: Prepared,
  source: UploadSource,
  options: TransferOptions = {},
): Promise<{ failed: number[] }> {
  let pending: readonly PartUrl[] = initial;
  let failed: number[] = [];
  for (let round = 1; round <= MAX_ROUNDS; round += 1) {
    const refused: number[] = [];
    await runBounded(
      pending,
      options.concurrency ?? CONCURRENCY,
      async (part) => {
        const slice = prepared.plan.parts[part.partNumber - 1];
        const digest = toBase64(prepared.digests[part.partNumber - 1]);
        try {
          await api.putPart(part.url, source.blob(slice.offset, slice.offset + slice.size), digest, options.signal);
        } catch (error) {
          if (isAbortError(error)) throw error;
          refused.push(part.partNumber);
          return;
        }
        options.onConfirmed?.(part.partNumber);
      },
      options.signal,
    );
    if (refused.length === 0) return { failed: [] };
    failed = refused;
    if (round === MAX_ROUNDS) break;
    const view = await api.read(sessionId);
    const again = new Set(refused);
    pending = view.parts.filter((part) => again.has(part.partNumber));
    if (pending.length === 0) return { failed: [] };
  }
  return { failed };
}

export interface StartInput {
  api: UploadApi;
  source: UploadSource;
  fields: DeclaredFields;
  fileName: string;
  dispatch: Dispatch;
  remember: (sessionId: string) => void;
}

/** Hashes the file and starts the session. A flagged duplicate stops here until the editor confirms it. */
export async function startUpload(input: StartInput): Promise<{ view: UploadSessionView; prepared: Prepared }> {
  const { api, source, fields, fileName, dispatch, remember } = input;
  dispatch({ type: 'selected', fileName, partCount: planParts(source.sizeBytes).partCount });
  const prepared = await prepareUpload(source, PART_SIZE_BYTES, (done, total) => dispatch({ type: 'hashed', done, total }));
  const view = await api.start(toUploadRequest(fields, prepared.checksum));
  remember(view.id);
  dispatch({
    type: 'sessionStarted',
    sessionId: view.id,
    flagged: view.state === 'flagged',
    duplicateOf: view.duplicateOf,
    confirmedParts: view.confirmedParts,
  });
  return { view, prepared };
}

export interface ContinueInput {
  api: UploadApi;
  source: UploadSource;
  prepared: Prepared;
  sessionId: string;
  parts: readonly PartUrl[];
  dispatch: Dispatch;
  signal?: AbortSignal;
}

/** Sends the missing parts and completes the upload; a part that keeps failing ends in a failed state with a retry. */
export async function continueUpload(input: ContinueInput): Promise<UploadSessionView | null> {
  const { api, source, prepared, sessionId, parts, dispatch, signal } = input;
  const { failed } = await transferParts(api, sessionId, parts, prepared, source, {
    signal,
    onConfirmed: (partNumber) => dispatch({ type: 'partConfirmed', partNumber }),
  });
  if (failed.length > 0) {
    dispatch({
      type: 'partsFailed',
      parts: failed,
      message: `Teil ${failed.join(', ')} konnte nicht übertragen werden. Erneut versuchen setzt die Übertragung fort.`,
    });
    return null;
  }
  dispatch({ type: 'completing' });
  try {
    const view = await api.complete(sessionId);
    dispatch({ type: 'completed', assetId: view.assetId ?? '' });
    return view;
  } catch (error) {
    dispatch({ type: 'failed', message: failureMessage(error) });
    return null;
  }
}

/**
 * Reads the session of an earlier upload and hashes the re-selected file. The file must be the one of the session:
 * its composite checksum has to equal the declared one, or nothing is sent.
 */
export async function resumeUpload(
  api: UploadApi,
  sessionId: string,
  source: UploadSource,
): Promise<{ view: UploadSessionView; prepared: Prepared } | { mismatch: true }> {
  const view = await api.read(sessionId);
  const prepared = await prepareUpload(source);
  if (!isSameUpload(view.checksum, prepared.checksum)) {
    return { mismatch: true };
  }
  return { view, prepared };
}

/** The sentence for a failure of any step: problems are mapped, anything else is reported as a network failure. */
export function failureMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'problem' in error) {
    return messageFor((error as { problem: ProblemView }).problem);
  }
  return 'Die Verbindung wurde unterbrochen. Erneut versuchen setzt die Übertragung fort.';
}
