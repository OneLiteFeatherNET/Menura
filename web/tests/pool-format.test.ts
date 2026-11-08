import { describe, expect, it } from 'vitest';
import {
  formatCreatedAt,
  formatDuration,
  formatTimestamp,
  stageLabel,
  transcriptLabel,
} from '../src/features/pool/format';

describe('duration as h:mm:ss', () => {
  it('shows 2:01:00 for 7260 seconds', () => {
    expect(formatDuration(7260)).toBe('2:01:00');
  });

  it('pads minutes and seconds to two digits', () => {
    expect(formatDuration(45)).toBe('0:00:45');
    expect(formatDuration(3605)).toBe('1:00:05');
  });

  it('shows a full two-and-a-half hour recording without rolling over', () => {
    expect(formatDuration(9000)).toBe('2:30:00');
  });

  it('treats a fractional or negative value as whole non-negative seconds', () => {
    expect(formatDuration(59.9)).toBe('0:00:59');
    expect(formatDuration(-4)).toBe('0:00:00');
  });
});

describe('timestamp of a transcript line', () => {
  it('shows minutes and seconds below one hour', () => {
    expect(formatTimestamp(12)).toBe('00:12');
    expect(formatTimestamp(3599)).toBe('59:59');
  });

  it('adds the hour from one hour on', () => {
    expect(formatTimestamp(3600)).toBe('1:00:00');
  });
});

describe('creation time in Europe/Berlin', () => {
  it('shows the Berlin wall-clock time, not UTC', () => {
    // 19:00 UTC in November is 20:00 in Berlin (CET, UTC+1).
    const text = formatCreatedAt('2026-11-05T19:00:00Z');

    expect(text).toContain('20:00');
  });
});

describe('German labels', () => {
  it('names the stages as the pool shows them', () => {
    expect(stageLabel('raw')).toBe('Roh');
    expect(stageLabel('cut')).toBe('Geschnitten');
    expect(stageLabel('rendered')).toBe('Fertig gerendert');
  });

  it('names the transcript states', () => {
    expect(transcriptLabel('ready')).toBe('Transkript bereit');
    expect(transcriptLabel('pending')).toBe('wird erstellt');
    expect(transcriptLabel('none')).toBe('kein Transkript');
  });
});
