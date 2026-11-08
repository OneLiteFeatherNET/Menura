import type { TranscriptSegment } from './types';

export interface TextPart {
  text: string;
  match: boolean;
}

function escapePattern(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Splits text into plain and matching parts for an in-page search. The match is case-insensitive and the query is
 * literal text. An empty query yields the whole text as one plain part.
 */
export function splitHighlight(text: string, query: string): TextPart[] {
  const needle = query.trim();
  if (needle === '') {
    return [{ text, match: false }];
  }
  // A capturing split puts every match at an odd index.
  const pieces = text.split(new RegExp(`(${escapePattern(needle)})`, 'giu'));
  return pieces
    .map((piece, index) => ({ text: piece, match: index % 2 === 1 }))
    .filter((part) => part.text !== '');
}

/** The number of matches of the query over all segment texts. */
export function countMatches(segments: readonly TranscriptSegment[], query: string): number {
  return segments.reduce(
    (total, segment) => total + splitHighlight(segment.text, query).filter((part) => part.match).length,
    0,
  );
}
