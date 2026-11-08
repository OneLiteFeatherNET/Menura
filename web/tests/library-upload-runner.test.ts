import { describe, expect, it } from 'vitest';
import type { PartUrl } from '../src/api/libraryUploads';
import { sha256, toBase64 } from '../src/library/upload/checksum';
import { planParts } from '../src/library/upload/partPlan';
import { prepareUpload, transferParts, type UploadSource } from '../src/library/upload/uploadRunner';

// An in-memory file: the runner reads it through the same slice interface the browser's File provides.
function memoryFile(content: string): UploadSource {
  const bytes = new TextEncoder().encode(content);
  return {
    sizeBytes: bytes.length,
    bytes: async (start, end) => bytes.slice(start, end),
    blob: (start, end) => new Blob([bytes.slice(start, end)]),
  };
}

// A storage that confirms the parts it receives. The first attempts of the parts named in failures are refused.
// Every presigned URL names its part and the attempt that issued it, so a test can tell fresh URLs from old ones.
class FakeStorage {
  readonly confirmed = new Set<number>();
  readonly putUrls: string[] = [];
  readonly digests = new Map<number, string>();
  private issued = 0;

  constructor(
    private readonly plan: ReturnType<typeof planParts>,
    private readonly refusals: Map<number, number>,
  ) {}

  readonly api = {
    read: async () => ({
      parts: this.plan.parts
        .filter((part) => !this.confirmed.has(part.partNumber))
        .map((part) => this.presign(part.partNumber, part.size)),
    }),
    putPart: async (url: string, _body: Blob, digest: string): Promise<void> => {
      const partNumber = Number(url.split('/')[3]);
      this.putUrls.push(url);
      this.digests.set(partNumber, digest);
      const left = this.refusals.get(partNumber) ?? 0;
      if (left > 0) {
        this.refusals.set(partNumber, left - 1);
        throw new Error('the storage refused the part');
      }
      this.confirmed.add(partNumber);
    },
  };

  initialParts(): PartUrl[] {
    return this.plan.parts.map((part) => this.presign(part.partNumber, part.size));
  }

  private presign(partNumber: number, sizeBytes: number): PartUrl {
    this.issued += 1;
    return {
      partNumber,
      sizeBytes,
      url: `https://storage/${partNumber}/${this.issued}`,
      expiresAt: '2026-11-08T12:00:00Z',
    };
  }
}

describe('preparing an upload', () => {
  it('hashes every part and derives the composite checksum with the part count', async () => {
    const prepared = await prepareUpload(memoryFile('abcdef'), 2);
    expect(prepared.plan.partCount).toBe(3);
    expect(prepared.checksum).toMatch(/^[A-Za-z0-9+/]{43}=-3$/);
    expect(prepared.digests.map(toBase64)).toEqual([
      toBase64(await sha256(new TextEncoder().encode('ab'))),
      toBase64(await sha256(new TextEncoder().encode('cd'))),
      toBase64(await sha256(new TextEncoder().encode('ef'))),
    ]);
  });

  it('reports the hashing progress part by part', async () => {
    const seen: number[] = [];
    await prepareUpload(memoryFile('abcdef'), 2, (done) => seen.push(done));
    expect(seen).toEqual([1, 2, 3]);
  });
});

describe('transferring the parts', () => {
  it('sends each part with its own SHA-256 checksum and confirms every part', async () => {
    const source = memoryFile('abcdef');
    const prepared = await prepareUpload(source, 2);
    const storage = new FakeStorage(prepared.plan, new Map());
    const confirmed: number[] = [];
    const result = await transferParts(storage.api, 's1', storage.initialParts(), prepared, source, {
      concurrency: 3,
      onConfirmed: (partNumber) => confirmed.push(partNumber),
    });
    expect(result).toEqual({ failed: [] });
    expect(confirmed.sort()).toEqual([1, 2, 3]);
    expect(storage.digests.get(2)).toBe(toBase64(prepared.digests[1] as Uint8Array));
  });

  it('asks for fresh part URLs when the storage refused a URL, and sends the part again', async () => {
    const source = memoryFile('abcdef');
    const prepared = await prepareUpload(source, 2);
    const storage = new FakeStorage(prepared.plan, new Map([[2, 1]]));
    const result = await transferParts(storage.api, 's1', storage.initialParts(), prepared, source, { concurrency: 1 });
    expect(result).toEqual({ failed: [] });
    expect([...storage.confirmed].sort()).toEqual([1, 2, 3]);
    expect(storage.putUrls.filter((url) => url.startsWith('https://storage/2/'))).toHaveLength(2);
  });

  it('reports the parts that still fail after the retry rounds, and keeps the others', async () => {
    const source = memoryFile('abcdef');
    const prepared = await prepareUpload(source, 2);
    const storage = new FakeStorage(prepared.plan, new Map([[3, 99]]));
    const result = await transferParts(storage.api, 's1', storage.initialParts(), prepared, source, { concurrency: 3 });
    expect(result).toEqual({ failed: [3] });
    expect([...storage.confirmed].sort()).toEqual([1, 2]);
  });
});
