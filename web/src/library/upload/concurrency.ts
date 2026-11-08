// Runs a worker over the items with at most `limit` of them in flight. A failure or an abort stops the scheduling of
// further items; the promise rejects once the items already in flight have settled.

export async function runBounded<T>(
  items: readonly T[],
  limit: number,
  worker: (item: T) => Promise<void>,
  signal?: AbortSignal,
): Promise<void> {
  let next = 0;
  let failure: unknown = null;
  let failed = false;

  const lane = async (): Promise<void> => {
    while (!failed && next < items.length) {
      if (signal?.aborted) {
        throw abortError();
      }
      const item = items[next] as T;
      next += 1;
      try {
        await worker(item);
      } catch (error) {
        if (!failed) {
          failed = true;
          failure = error;
        }
      }
    }
  };

  const lanes = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, () => lane());
  await Promise.all(lanes);
  if (failed) {
    throw failure;
  }
  if (signal?.aborted && next < items.length) {
    throw abortError();
  }
}

/** The error an aborted operation rejects with; the name is what a fetch rejects with too. */
export function abortError(): DOMException {
  return new DOMException('The operation was aborted.', 'AbortError');
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}
