// The split of a file into the parts of a presigned multipart upload. The size is the server's default part size
// (95 MiB, principle 15: every request body stays below the 100 MB proxy limit); the server refuses a checksum whose
// part count does not match this plan.

/** 95 MiB in bytes, the server's default part size (MENURA_LIBRARY_PART_SIZE_BYTES). */
export const PART_SIZE_BYTES = 95 * 1024 * 1024;

/** The S3 limit on the number of parts of one multipart upload. */
const MAX_PARTS = 10_000;

export interface PartSlice {
  /** The part number, starting at 1 as S3 numbers the parts. */
  partNumber: number;
  /** The byte offset at which the part starts in the file. */
  offset: number;
  /** The size of the part in bytes; every part but the last has the plan's part size. */
  size: number;
}

export interface PartPlan {
  sizeBytes: number;
  partSizeBytes: number;
  partCount: number;
  parts: PartSlice[];
}

/** Splits a file of sizeBytes into parts of partSizeBytes. An empty file or a plan above 10 000 parts is refused. */
export function planParts(sizeBytes: number, partSizeBytes: number = PART_SIZE_BYTES): PartPlan {
  if (!Number.isInteger(sizeBytes) || sizeBytes < 1) {
    throw new RangeError('a file must have at least one byte');
  }
  if (!Number.isInteger(partSizeBytes) || partSizeBytes < 1) {
    throw new RangeError('a part must have at least one byte');
  }
  const partCount = Math.ceil(sizeBytes / partSizeBytes);
  if (partCount > MAX_PARTS) {
    throw new RangeError(`a file of ${partCount} parts exceeds the ${MAX_PARTS} parts of S3`);
  }
  const parts: PartSlice[] = [];
  for (let index = 0; index < partCount; index += 1) {
    const offset = index * partSizeBytes;
    parts.push({ partNumber: index + 1, offset, size: Math.min(partSizeBytes, sizeBytes - offset) });
  }
  return { sizeBytes, partSizeBytes, partCount, parts };
}

/** The parts that the storage has not confirmed yet, in part order. */
export function remainingParts(plan: PartPlan, confirmed: readonly number[]): PartSlice[] {
  const done = new Set(confirmed);
  return plan.parts.filter((part) => !done.has(part.partNumber));
}
