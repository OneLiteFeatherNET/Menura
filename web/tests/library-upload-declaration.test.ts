import { describe, expect, it } from 'vitest';
import {
  checkDeclaration,
  formatOf,
  kindForFormat,
  toUploadRequest,
  type DeclarationInput,
} from '../src/library/upload/declaration';

const RAW_INPUT: DeclarationInput = {
  mode: 'raw',
  fileName: 'stream.mkv',
  sizeBytes: 1000,
  durationSeconds: '3600',
  width: '1920',
  height: '1080',
  streamTitle: ' Coop-Abend ',
  streamDate: '2026-11-01',
};

const VIDEO_INPUT: DeclarationInput = {
  ...RAW_INPUT,
  mode: 'finished',
  fileName: 'render.mp4',
  streamTitle: '',
  streamDate: '',
};

const IMAGE_INPUT: DeclarationInput = {
  ...VIDEO_INPUT,
  fileName: 'thumb.PNG',
  durationSeconds: '',
};

describe('format and kind of a file', () => {
  it('reads the lower-case extension of the file name', () => {
    expect(formatOf('Clip.MP4')).toBe('mp4');
    expect(formatOf('README')).toBe('');
  });

  it('maps video containers to video and image formats to image', () => {
    expect(kindForFormat('mkv')).toBe('video');
    expect(kindForFormat('webp')).toBe('image');
    expect(kindForFormat('exe')).toBeNull();
  });
});

describe('declaration check', () => {
  it('accepts a raw recording with its stream title and date, trimming the title', () => {
    const result = checkDeclaration(RAW_INPUT);
    expect(result).toEqual({
      ok: true,
      fields: {
        kind: 'raw_material',
        format: 'mkv',
        durationSeconds: 3600,
        width: 1920,
        height: 1080,
        sizeBytes: 1000,
        streamTitle: 'Coop-Abend',
        streamDate: '2026-11-01',
      },
    });
  });

  it('asks for a stream title for raw material', () => {
    const result = checkDeclaration({ ...RAW_INPUT, streamTitle: '   ' });
    expect(result).toMatchObject({ ok: false, errors: { streamTitle: expect.any(String) } });
  });

  it('refuses a stream date that is not a real day', () => {
    const result = checkDeclaration({ ...RAW_INPUT, streamDate: '2026-02-30' });
    expect(result).toMatchObject({ ok: false, errors: { streamDate: expect.any(String) } });
  });

  it('refuses a control character in the stream title', () => {
    const result = checkDeclaration({ ...RAW_INPUT, streamTitle: 'Coop\u0007' });
    expect(result).toMatchObject({ ok: false, errors: { streamTitle: expect.any(String) } });
  });

  it('accepts raw material only as a video container', () => {
    const result = checkDeclaration({ ...RAW_INPUT, fileName: 'still.png' });
    expect(result).toMatchObject({ ok: false, errors: { format: expect.any(String) } });
  });

  it('asks for a duration for a finished video', () => {
    const result = checkDeclaration({ ...VIDEO_INPUT, durationSeconds: '' });
    expect(result).toMatchObject({ ok: false, errors: { durationSeconds: expect.any(String) } });
  });

  it('keeps no duration for a finished image', () => {
    const result = checkDeclaration(IMAGE_INPUT);
    expect(result).toMatchObject({ ok: true, fields: { kind: 'image', durationSeconds: null } });
  });

  it('refuses a resolution that is not a whole number above zero', () => {
    expect(checkDeclaration({ ...VIDEO_INPUT, width: '0' })).toMatchObject({ ok: false, errors: { width: expect.any(String) } });
    expect(checkDeclaration({ ...VIDEO_INPUT, height: '1.5' })).toMatchObject({ ok: false, errors: { height: expect.any(String) } });
  });

  it('refuses a file type that is neither a video nor an image', () => {
    const result = checkDeclaration({ ...VIDEO_INPUT, fileName: 'setup.exe' });
    expect(result).toMatchObject({ ok: false, errors: { format: expect.any(String) } });
  });
});

describe('upload request body', () => {
  it('carries the checksum next to the checked fields', () => {
    const check = checkDeclaration(RAW_INPUT);
    if (!check.ok) throw new Error('the raw input must be valid');
    expect(toUploadRequest(check.fields, 'abc=-1')).toEqual({
      kind: 'raw_material',
      format: 'mkv',
      durationSeconds: 3600,
      width: 1920,
      height: 1080,
      sizeBytes: 1000,
      checksum: 'abc=-1',
      streamTitle: 'Coop-Abend',
      streamDate: '2026-11-01',
    });
  });

  it('leaves out the duration and the stream fields of an image', () => {
    const check = checkDeclaration(IMAGE_INPUT);
    if (!check.ok) throw new Error('the image input must be valid');
    const body = toUploadRequest(check.fields, 'abc=-1');
    expect(body).not.toHaveProperty('durationSeconds');
    expect(body).not.toHaveProperty('streamTitle');
    expect(body).not.toHaveProperty('streamDate');
  });
});
