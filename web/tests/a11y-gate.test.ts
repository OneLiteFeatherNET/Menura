import axe from 'axe-core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { expectNoSeriousA11yViolations } from '../.storybook/a11y';

// axe needs a rendered page, so these tests replace it with fake results. They check only the gate: which impacts
// fail a story and which are reported as warnings.
vi.mock('axe-core', () => ({ default: { run: vi.fn() } }));

function fakeViolation(id: string, impact: 'minor' | 'moderate' | 'serious' | 'critical') {
  return { id, impact, help: `help for ${id}`, nodes: [{ target: ['button'] }] };
}

function axeReturns(violations: ReturnType<typeof fakeViolation>[]) {
  vi.mocked(axe.run).mockResolvedValueOnce({ violations } as unknown as Awaited<ReturnType<typeof axe.run>>);
}

describe('expectNoSeriousA11yViolations', () => {
  let warnings: unknown[][];

  beforeEach(() => {
    warnings = [];
    vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      warnings.push(args);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fails when a story has a serious violation and names the rule', async () => {
    axeReturns([fakeViolation('label', 'serious')]);

    await expect(expectNoSeriousA11yViolations(document.createElement('div'))).rejects.toThrow(/label/);
  });

  it('passes when a story has only a moderate violation and reports it as a warning', async () => {
    axeReturns([fakeViolation('region', 'moderate')]);

    await expect(expectNoSeriousA11yViolations(document.createElement('div'))).resolves.toBeUndefined();
    expect(
      warnings.map((args) => args.join(' ')),
      'the moderate violation must be reported as a warning naming its rule',
    ).toEqual([expect.stringContaining('region')]);
  });
});
