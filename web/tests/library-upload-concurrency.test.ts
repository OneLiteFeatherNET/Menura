import { describe, expect, it } from 'vitest';
import { runBounded } from '../src/library/upload/concurrency';

describe('bounded concurrency', () => {
  it('runs every item and never more than the limit at once', async () => {
    let active = 0;
    let peak = 0;
    const done: number[] = [];
    await runBounded([1, 2, 3, 4, 5], 2, async (item) => {
      active += 1;
      peak = Math.max(peak, active);
      await Promise.resolve();
      active -= 1;
      done.push(item);
    });
    expect(peak, 'at most the limit runs at once').toBeLessThanOrEqual(2);
    expect([...done].sort(), 'every item is run once').toEqual([1, 2, 3, 4, 5]);
  });

  it('stops scheduling new items after the first failure and rejects with it', async () => {
    const started: number[] = [];
    const run = runBounded([1, 2, 3, 4], 1, async (item) => {
      started.push(item);
      if (item === 2) throw new Error('part 2 failed');
    });
    await expect(run).rejects.toThrow('part 2 failed');
    expect(started, 'no item after the failing one is started').toEqual([1, 2]);
  });

  it('stops and rejects with an AbortError when the signal is aborted', async () => {
    const controller = new AbortController();
    const started: number[] = [];
    const run = runBounded(
      [1, 2, 3],
      1,
      async (item) => {
        started.push(item);
        controller.abort();
      },
      controller.signal,
    );
    await expect(run).rejects.toMatchObject({ name: 'AbortError' });
    expect(started).toEqual([1]);
  });

  it('starts nothing when the signal is already aborted', async () => {
    const controller = new AbortController();
    controller.abort();
    const started: number[] = [];
    await expect(runBounded([1, 2], 2, async (item) => void started.push(item), controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
    expect(started).toEqual([]);
  });
});
