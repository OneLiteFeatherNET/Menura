import { describe, expect, it } from 'vitest';
import { EMPTY_FILTERS, poolQuery } from '../src/library/pool';

describe('pool query', () => {
  it('asks only for the first page of the whole pool when no filter is chosen', () => {
    expect(poolQuery(EMPTY_FILTERS)).toBe('limit=50');
  });

  it('sends the caller-only filter as mine=true', () => {
    expect(poolQuery({ ...EMPTY_FILTERS, mine: true })).toBe('mine=true&limit=50');
  });

  it('sends a trimmed free tag and leaves an empty one out', () => {
    expect(poolQuery({ ...EMPTY_FILTERS, tag: '  speedrun ' })).toBe('tag=speedrun&limit=50');
    expect(poolQuery({ ...EMPTY_FILTERS, tag: '   ' })).toBe('limit=50');
  });

  it('combines stage and usage with the other filters', () => {
    expect(poolQuery({ mine: false, tag: '', stage: 'cut', usage: 'never_used' })).toBe(
      'stage=cut&usage=never_used&limit=50',
    );
  });

  it('appends the cursor of the next page', () => {
    expect(poolQuery(EMPTY_FILTERS, 'abc', 20)).toBe('limit=20&cursor=abc');
  });
});
