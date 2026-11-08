import { describe, expect, it } from 'vitest';
import { messageFor, problemFromBody } from '../src/library/upload/problems';
import { transcriptContentType, transcriptErrorLine, transcriptFormatOf } from '../src/library/upload/transcript';

describe('problem details', () => {
  it('reads the status, title and detail of a Problem Details body', () => {
    expect(problemFromBody({ title: 'Checksum Mismatch', detail: 'x', status: 422 }, 422)).toEqual({
      status: 422,
      title: 'Checksum Mismatch',
      detail: 'x',
    });
  });

  it('falls back to the HTTP status when the body is not a problem', () => {
    expect(problemFromBody(null, 500)).toEqual({ status: 500, title: '', detail: '' });
    expect(problemFromBody('<html>', 502)).toEqual({ status: 502, title: '', detail: '' });
  });

  it('tells the editor that a checksum mismatch dropped the upload', () => {
    const message = messageFor({ status: 422, title: 'Checksum Mismatch', detail: '' });
    expect(message).toBe('Die gespeicherte Datei stimmt nicht mit der angegebenen überein. Der Upload wurde verworfen.');
  });

  it('explains that the upload continues when storage is unavailable', () => {
    const message = messageFor({ status: 503, title: '', detail: '' });
    expect(message).toContain('Speicher');
  });

  it('names the line of a transcript that cannot be parsed', () => {
    const message = messageFor({ status: 400, title: 'Invalid transcript', detail: 'At line 7: the timing line is malformed' });
    expect(message).toContain('Zeile 7');
  });

  it('shows the size limit of a transcript', () => {
    expect(messageFor({ status: 413, title: 'Transcript Too Large', detail: '' })).toContain('10 MB');
  });

  it('falls back to a message that names the status', () => {
    expect(messageFor({ status: 418, title: '', detail: '' })).toBe('Die Anfrage ist fehlgeschlagen (HTTP 418).');
  });
});

describe('transcript file', () => {
  it('recognises WebVTT and SRT files by their extension, in any case', () => {
    expect(transcriptFormatOf('captions.vtt')).toBe('webvtt');
    expect(transcriptFormatOf('subs.SRT')).toBe('srt');
    expect(transcriptFormatOf('notes.txt')).toBeNull();
  });

  it('sends each format with its media type', () => {
    expect(transcriptContentType('webvtt')).toBe('text/vtt');
    expect(transcriptContentType('srt')).toBe('application/x-subrip');
  });

  it('reads the line number from a parse error', () => {
    expect(transcriptErrorLine('At line 12: cue 2 has no end time')).toBe(12);
    expect(transcriptErrorLine('The file contains no cue with a timing line.')).toBeNull();
  });
});
