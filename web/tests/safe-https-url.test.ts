import { describe, expect, it } from 'vitest';
import { safeHttpsUrl } from '../src/domain/url';

describe('safeHttpsUrl', () => {
  it('keeps an https url', () => {
    expect(safeHttpsUrl('https://www.youtube.com/playlist?list=PL123')).toBe(
      'https://www.youtube.com/playlist?list=PL123',
    );
  });

  it('rejects plain http', () => {
    expect(safeHttpsUrl('http://example.com/playlist')).toBeUndefined();
  });

  it('rejects javascript: urls', () => {
    expect(safeHttpsUrl('javascript:alert(1)')).toBeUndefined();
  });

  it('rejects data: urls', () => {
    expect(safeHttpsUrl('data:text/html,<script>alert(1)</script>')).toBeUndefined();
  });

  it('rejects relative urls', () => {
    expect(safeHttpsUrl('/playlist/1')).toBeUndefined();
    expect(safeHttpsUrl('playlist/1')).toBeUndefined();
  });

  it('rejects garbage', () => {
    expect(safeHttpsUrl('not a url')).toBeUndefined();
    expect(safeHttpsUrl('')).toBeUndefined();
  });

  it('rejects values that are not strings', () => {
    expect(safeHttpsUrl(undefined)).toBeUndefined();
    expect(safeHttpsUrl(null)).toBeUndefined();
    expect(safeHttpsUrl(42)).toBeUndefined();
  });
});
