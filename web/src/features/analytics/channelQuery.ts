/** The query parameter that holds the selected channels, for example ?kanaele=yt-de,tt-de. */
export const CHANNEL_PARAM = 'kanaele';

/**
 * The selected channels of a query value. No parameter selects every channel; an empty parameter selects none.
 * Ids that are not known channels are dropped, and the result keeps the channel order.
 */
export function selectedFromQuery(value: unknown, allIds: string[]): string[] {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string') {
    return [...allIds];
  }
  const wanted = new Set(raw.split(',').map((id) => id.trim()).filter((id) => id !== ''));
  return allIds.filter((id) => wanted.has(id));
}

/** The query value of a selection. Every channel selected is the default, so the parameter is dropped. */
export function queryFromSelection(selected: string[], allIds: string[]): string | undefined {
  const ordered = allIds.filter((id) => selected.includes(id));
  if (ordered.length === allIds.length) {
    return undefined;
  }
  return ordered.join(',');
}
