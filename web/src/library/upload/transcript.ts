// Transcript files of a raw item: WebVTT or SRT, chosen by the file extension. The server parses the file and names the
// line of a malformed cue ("At line N: ..."); this module reads that line back for the editor.

export type TranscriptFormat = 'webvtt' | 'srt';

/** The format of a transcript file by its extension, or null for any other file. */
export function transcriptFormatOf(fileName: string): TranscriptFormat | null {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.vtt')) return 'webvtt';
  if (lower.endsWith('.srt')) return 'srt';
  return null;
}

/** The media type the transcript is sent with (contracts/openapi/menura.yaml, uploadTranscript). */
export function transcriptContentType(format: TranscriptFormat): string {
  return format === 'webvtt' ? 'text/vtt' : 'application/x-subrip';
}

/** The line number a parse error names, or null when the detail names none. */
export function transcriptErrorLine(detail: string): number | null {
  const match = /At line (\d+)/.exec(detail);
  return match ? Number(match[1]) : null;
}
