import { describe, expect, it } from 'vitest';
import { forgetUpload, isSameUpload, recallUpload, rememberUpload, type ResumeStorage } from '../src/library/upload/resume';

class MemoryStorage implements ResumeStorage {
  private readonly values = new Map<string, string>();
  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }
  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
  removeItem(key: string): void {
    this.values.delete(key);
  }
}

class BlockedStorage implements ResumeStorage {
  getItem(): string | null {
    throw new DOMException('blocked', 'SecurityError');
  }
  setItem(): void {
    throw new DOMException('blocked', 'SecurityError');
  }
  removeItem(): void {
    throw new DOMException('blocked', 'SecurityError');
  }
}

const SESSION = '4f0c7a52-9d1e-4b8a-8e3f-2a6c5d7b9e10';

describe('resume memory in the browser', () => {
  it('recalls the session that was remembered', () => {
    const storage = new MemoryStorage();
    rememberUpload(storage, SESSION);
    expect(recallUpload(storage)).toBe(SESSION);
  });

  it('forgets the session once the upload has ended', () => {
    const storage = new MemoryStorage();
    rememberUpload(storage, SESSION);
    forgetUpload(storage);
    expect(recallUpload(storage)).toBeNull();
  });

  it('works without any memory when browser storage is blocked', () => {
    const storage = new BlockedStorage();
    expect(() => rememberUpload(storage, SESSION)).not.toThrow();
    expect(recallUpload(storage)).toBeNull();
    expect(() => forgetUpload(storage)).not.toThrow();
  });
});

describe('identity of a re-selected file', () => {
  it('accepts a file whose composite checksum equals the declared one', () => {
    expect(isSameUpload('abc=-2', 'abc=-2')).toBe(true);
  });

  it('refuses a different file, even one of the same size', () => {
    expect(isSameUpload('abc=-2', 'xyz=-2')).toBe(false);
  });
});
