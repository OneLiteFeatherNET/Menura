import { describe, expect, it } from 'vitest';
import { compositeChecksum, hashParts, sha256, toBase64 } from '../src/library/upload/checksum';
import { planParts } from '../src/library/upload/partPlan';

const ABC = new TextEncoder().encode('abc');
const EMPTY = new Uint8Array();

// SHA-256 of "abc" (FIPS 180-2 example), base64 encoded.
const DIGEST_ABC = 'ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=';
// Composite values as S3 reports them: base64(SHA-256(concatenated part digests)) and the part count.
const COMPOSITE_ONE_PART = 'T4tCwi3TcptRm6b2jS2nzFstYG0F2u1a1RKMwD5sY1g=-1';
const COMPOSITE_TWO_PARTS = 'bxKQiW7oGgNJF00Z9Ec9JnoQKJxASAhh1cQq//+9efk=-2';

describe('checksum', () => {
  it('encodes bytes as standard base64', () => {
    expect(toBase64(new Uint8Array([0xff]))).toBe('/w==');
    expect(toBase64(new Uint8Array([0x66, 0x6f, 0x6f]))).toBe('Zm9v');
  });

  it('computes the SHA-256 digest of a part', async () => {
    expect(toBase64(await sha256(ABC))).toBe(DIGEST_ABC);
  });

  it('computes one digest per part, in part order', async () => {
    const bytes = new TextEncoder().encode('abc');
    const plan = planParts(bytes.length, 2);
    const digests = await hashParts(plan, async (start, end) => bytes.slice(start, end));
    expect(digests.map(toBase64)).toEqual([
      toBase64(await sha256(new TextEncoder().encode('ab'))),
      toBase64(await sha256(new TextEncoder().encode('c'))),
    ]);
  });

  it('reports the progress of hashing part by part', async () => {
    const plan = planParts(6, 2);
    const seen: Array<[number, number]> = [];
    await hashParts(plan, async () => new Uint8Array(2), (done, total) => seen.push([done, total]));
    expect(seen).toEqual([
      [1, 3],
      [2, 3],
      [3, 3],
    ]);
  });

  it('builds the composite checksum of a single part', async () => {
    expect(await compositeChecksum([await sha256(ABC)])).toBe(COMPOSITE_ONE_PART);
  });

  it('names the part count after the dash', async () => {
    const digests = [await sha256(ABC), await sha256(EMPTY)];
    expect(await compositeChecksum(digests)).toBe(COMPOSITE_TWO_PARTS);
  });

  it('depends on the order of the parts', async () => {
    const first = await sha256(ABC);
    const second = await sha256(EMPTY);
    expect(await compositeChecksum([second, first])).not.toBe(COMPOSITE_TWO_PARTS);
  });
});
