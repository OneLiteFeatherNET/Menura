/**
 * The value as an absolute https URL, or undefined. Links from API data are only rendered through
 * this guard, so javascript:, data:, http: and relative values never become an href.
 */
export function safeHttpsUrl(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}
