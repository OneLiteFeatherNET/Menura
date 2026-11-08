import { describe, expect, it } from 'vitest';
import { IDLE_UPLOAD, percentOf, reduceUpload, type UploadState } from '../src/library/upload/uploadState';

function selected(): UploadState {
  return reduceUpload(IDLE_UPLOAD, { type: 'selected', fileName: 'render.mp4', partCount: 4 });
}

describe('upload state', () => {
  it('starts hashing when a file is selected', () => {
    const state = selected();
    expect(state).toMatchObject({ phase: 'hashing', fileName: 'render.mp4', partCount: 4, hashedParts: 0 });
  });

  it('counts the hashed parts', () => {
    const state = reduceUpload(selected(), { type: 'hashed', done: 2, total: 4 });
    expect(state.hashedParts).toBe(2);
    expect(percentOf(state)).toBe(50);
  });

  it('shows a flagged duplicate and waits for the editor', () => {
    const state = reduceUpload(selected(), {
      type: 'sessionStarted',
      sessionId: 's1',
      flagged: true,
      duplicateOf: 'a1',
      confirmedParts: [],
    });
    expect(state).toMatchObject({ phase: 'flagged', duplicateOf: 'a1', sessionId: 's1' });
  });

  it('moves a confirmed duplicate on to uploading', () => {
    const flagged = reduceUpload(selected(), { type: 'sessionStarted', sessionId: 's1', flagged: true, duplicateOf: 'a1', confirmedParts: [] });
    expect(reduceUpload(flagged, { type: 'duplicateConfirmed' }).phase).toBe('uploading');
  });

  it('counts each confirmed part once, whatever the order of the reports', () => {
    let state = reduceUpload(selected(), { type: 'sessionStarted', sessionId: 's1', flagged: false, duplicateOf: null, confirmedParts: [1] });
    state = reduceUpload(state, { type: 'partConfirmed', partNumber: 3 });
    state = reduceUpload(state, { type: 'partConfirmed', partNumber: 2 });
    state = reduceUpload(state, { type: 'partConfirmed', partNumber: 2 });
    expect(state.confirmedParts).toEqual([1, 2, 3]);
    expect(percentOf(state)).toBe(75);
  });

  it('keeps the failed parts and their message, so the editor can retry', () => {
    const state = reduceUpload(selected(), { type: 'partsFailed', parts: [3], message: 'Teil 3 konnte nicht übertragen werden.' });
    expect(state).toMatchObject({ phase: 'failed', failedParts: [3], message: 'Teil 3 konnte nicht übertragen werden.' });
  });

  it('completes with the asset id', () => {
    const state = reduceUpload(selected(), { type: 'completed', assetId: 'a2' });
    expect(state).toMatchObject({ phase: 'completed', assetId: 'a2' });
    expect(percentOf(state)).toBe(100);
  });

  it('shows a failure of the whole upload with its message', () => {
    const state = reduceUpload(selected(), { type: 'failed', message: 'Die Prüfsumme stimmt nicht.' });
    expect(state).toMatchObject({ phase: 'failed', message: 'Die Prüfsumme stimmt nicht.' });
  });

  it('ends a cancelled upload without an asset', () => {
    const state = reduceUpload(selected(), { type: 'cancelled' });
    expect(state).toMatchObject({ phase: 'cancelled', assetId: null });
  });
});
