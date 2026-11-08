import { describe, expect, it } from 'vitest';
import { PART_SIZE_BYTES, planParts, remainingParts } from '../src/library/upload/partPlan';

describe('part plan', () => {
  it('uses parts of 95 MiB, the server default that keeps each request below the 100 MB proxy limit', () => {
    expect(PART_SIZE_BYTES).toBe(99614720);
  });

  it('needs one part for a file that fits into one part', () => {
    const plan = planParts(PART_SIZE_BYTES);
    expect(plan.partCount).toBe(1);
    expect(plan.parts).toEqual([{ partNumber: 1, offset: 0, size: PART_SIZE_BYTES }]);
  });

  it('gives every part but the last the configured size and the rest to the last part', () => {
    const plan = planParts(10, 4);
    expect(plan.partCount).toBe(3);
    expect(plan.parts).toEqual([
      { partNumber: 1, offset: 0, size: 4 },
      { partNumber: 2, offset: 4, size: 4 },
      { partNumber: 3, offset: 8, size: 2 },
    ]);
  });

  it('numbers the parts from 1 as S3 does', () => {
    expect(planParts(9, 3).parts.map((part) => part.partNumber)).toEqual([1, 2, 3]);
  });

  it('refuses an empty file', () => {
    expect(() => planParts(0)).toThrow(RangeError);
  });

  it('refuses a plan above the 10 000 parts of S3', () => {
    expect(() => planParts(10_001, 1)).toThrow(RangeError);
  });

  it('lists only the parts that are not confirmed yet, in order', () => {
    const plan = planParts(10, 4);
    expect(remainingParts(plan, [1, 3]).map((part) => part.partNumber)).toEqual([2]);
    expect(remainingParts(plan, [])).toHaveLength(3);
    expect(remainingParts(plan, [1, 2, 3])).toEqual([]);
  });
});
