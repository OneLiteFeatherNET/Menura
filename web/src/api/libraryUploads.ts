// The upload sessions of finished assets and raw material, and the transcript of a raw item (contracts/openapi/
// menura.yaml, Library and Content). The API carries metadata and presigned part URLs only; the media bytes go from
// the browser straight to the object storage, with no credential in the browser (principle 1 and 6).

import { failureOf } from '../library/upload/problems';
import { apiBase, type Fetcher } from '../auth/session';
import type { UploadRequestBody } from '../library/upload/declaration';
import type { TranscriptFormat } from '../library/upload/transcript';
import { transcriptContentType } from '../library/upload/transcript';

export type UploadSessionState = 'flagged' | 'uploading' | 'available' | 'failed' | 'cancelled';

/** A presigned PUT URL for one part; the browser sends the part with its x-amz-checksum-sha256 header. */
export interface PartUrl {
  partNumber: number;
  sizeBytes: number;
  url: string;
  expiresAt: string;
}

export interface UploadSessionView {
  id: string;
  state: UploadSessionState;
  kind: 'video' | 'image' | 'raw_material';
  format: string;
  durationSeconds: number | null;
  width: number;
  height: number;
  sizeBytes: number;
  checksum: string;
  partSizeBytes: number;
  partCount: number;
  streamTitle: string | null;
  streamDate: string | null;
  duplicateOf: string | null;
  assetId: string | null;
  failureReason: string | null;
  confirmedParts: number[];
  nextPart: number | null;
  parts: PartUrl[];
}

export interface TranscriptView {
  rawItemId: string;
  format: TranscriptFormat;
  segmentCount: number;
  replaced: boolean;
  uploadedAt: string;
}

/** A failed storage request. Status 403 on a part means its presigned URL has expired. */
export class PartRefused extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`Der Teil wurde vom Speicher abgelehnt (HTTP ${status}).`);
    this.name = 'PartRefused';
    this.status = status;
  }
}

async function sendJson(path: string, init: RequestInit, fetcher: Fetcher): Promise<UploadSessionView> {
  const response = await fetcher(`${apiBase()}${path}`, {
    ...init,
    credentials: 'include',
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}) },
  });
  if (!response.ok) {
    throw await failureOf(response);
  }
  return (await response.json()) as UploadSessionView;
}

export function startAssetUpload(body: UploadRequestBody, fetcher: Fetcher = fetch): Promise<UploadSessionView> {
  return sendJson('/asset-upload-sessions', { method: 'POST', body: JSON.stringify(body) }, fetcher);
}

export function readAssetUpload(id: string, fetcher: Fetcher = fetch): Promise<UploadSessionView> {
  return sendJson(`/asset-upload-sessions/${encodeURIComponent(id)}`, { method: 'GET' }, fetcher);
}

export function confirmDuplicateAssetUpload(id: string, fetcher: Fetcher = fetch): Promise<UploadSessionView> {
  return sendJson(`/asset-upload-sessions/${encodeURIComponent(id)}/duplicate-confirmation`, { method: 'POST' }, fetcher);
}

export function completeAssetUpload(id: string, fetcher: Fetcher = fetch): Promise<UploadSessionView> {
  return sendJson(`/asset-upload-sessions/${encodeURIComponent(id)}/complete`, { method: 'POST' }, fetcher);
}

export function cancelAssetUpload(id: string, fetcher: Fetcher = fetch): Promise<UploadSessionView> {
  return sendJson(`/asset-upload-sessions/${encodeURIComponent(id)}`, { method: 'DELETE' }, fetcher);
}

/**
 * Sends one part to the storage with its SHA-256 (base64). The storage answers 200 when the part is stored and the
 * checksum matches; the request carries no credentials, because the presigned URL is the credential.
 */
export async function putPart(
  url: string,
  body: Blob,
  sha256Base64: string,
  signal?: AbortSignal,
  fetcher: Fetcher = fetch,
): Promise<void> {
  const response = await fetcher(url, {
    method: 'PUT',
    body,
    signal,
    credentials: 'omit',
    headers: { 'x-amz-checksum-sha256': sha256Base64 },
  });
  if (!response.ok) {
    throw new PartRefused(response.status);
  }
}

/** Attaches or replaces the transcript of a raw item. A malformed file is answered with a line-numbered problem. */
export async function uploadTranscript(
  rawItemId: string,
  file: Blob,
  format: TranscriptFormat,
  signal?: AbortSignal,
  fetcher: Fetcher = fetch,
): Promise<TranscriptView> {
  const response = await fetcher(`${apiBase()}/library/raw-items/${encodeURIComponent(rawItemId)}/transcript`, {
    method: 'PUT',
    body: file,
    signal,
    credentials: 'include',
    headers: { 'Content-Type': transcriptContentType(format), Accept: 'application/json' },
  });
  if (!response.ok) {
    throw await failureOf(response);
  }
  return (await response.json()) as TranscriptView;
}
