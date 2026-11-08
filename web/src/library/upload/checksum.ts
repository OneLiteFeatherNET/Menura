// The checksums of an upload, computed in the browser with Web Crypto. Each part gets its own SHA-256 (sent as the
// x-amz-checksum-sha256 header of the part), and the file gets the composite value the server compares with the
// storage on completion: base64(SHA-256(concatenated part digests)) followed by a dash and the part count.

import type { PartPlan } from './partPlan';

/** Standard base64 of bytes, with padding. */
export function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

/** The SHA-256 digest of bytes. */
export async function sha256(bytes: Uint8Array): Promise<Uint8Array> {
  const digest = await crypto.subtle.digest('SHA-256', bytes as BufferSource);
  return new Uint8Array(digest);
}

/**
 * The SHA-256 digest of every part, in part order. readSlice returns the bytes between start (inclusive) and end
 * (exclusive); the parts are read one at a time, so a large file never sits in memory as a whole.
 */
export async function hashParts(
  plan: PartPlan,
  readSlice: (start: number, end: number) => Promise<Uint8Array>,
  onPart?: (done: number, total: number) => void,
): Promise<Uint8Array[]> {
  const digests: Uint8Array[] = [];
  for (const part of plan.parts) {
    const bytes = await readSlice(part.offset, part.offset + part.size);
    digests.push(await sha256(bytes));
    onPart?.(digests.length, plan.partCount);
  }
  return digests;
}

/** The composite checksum the server expects: base64 of SHA-256 over the part digests, dash, part count. */
export async function compositeChecksum(digests: readonly Uint8Array[]): Promise<string> {
  const total = digests.reduce((sum, digest) => sum + digest.length, 0);
  const joined = new Uint8Array(total);
  let offset = 0;
  for (const digest of digests) {
    joined.set(digest, offset);
    offset += digest.length;
  }
  return `${toBase64(await sha256(joined))}-${digests.length}`;
}
