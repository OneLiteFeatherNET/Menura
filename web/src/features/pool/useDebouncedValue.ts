import { onScopeDispose, ref, watch, type Ref } from 'vue';

/** The debounce of the pool search: a text is applied after this many milliseconds without a new keystroke. */
export const SEARCH_DEBOUNCE_MS = 250;

/**
 * A copy of the source that follows it only after `delay` milliseconds without a change. The timer is the global
 * setTimeout, so tests fake it with vi.useFakeTimers. The watcher runs synchronously, so the timer starts at the
 * change itself.
 */
export function useDebouncedValue<T>(source: Ref<T>, delay: number = SEARCH_DEBOUNCE_MS): Ref<T> {
  const debounced = ref(source.value) as Ref<T>;
  let timer: ReturnType<typeof setTimeout> | undefined;

  watch(
    source,
    (value) => {
      if (timer !== undefined) {
        clearTimeout(timer);
      }
      timer = setTimeout(() => {
        timer = undefined;
        debounced.value = value;
      }, delay);
    },
    { flush: 'sync' },
  );

  onScopeDispose(() => {
    if (timer !== undefined) {
      clearTimeout(timer);
    }
  });

  return debounced;
}
