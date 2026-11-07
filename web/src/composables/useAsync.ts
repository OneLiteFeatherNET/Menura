import { ref, shallowRef } from 'vue';
import { errorMessage } from '../api/client';

/**
 * Loads data for a view. Only the latest call may write its result, so a slow earlier request
 * cannot overwrite a newer one (for example after the user changes a week selection).
 */
export function useAsync<T>(load: () => Promise<T>) {
  const data = shallowRef<T | null>(null);
  const error = ref<string | null>(null);
  const loading = ref(true);
  let latest = 0;

  async function run(): Promise<void> {
    const request = ++latest;
    loading.value = true;
    error.value = null;
    try {
      const result = await load();
      if (request === latest) {
        data.value = result;
      }
    } catch (cause) {
      if (request === latest) {
        error.value = errorMessage(cause);
      }
    } finally {
      if (request === latest) {
        loading.value = false;
      }
    }
  }

  return { data, error, loading, run };
}
