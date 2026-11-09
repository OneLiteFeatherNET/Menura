import axe from 'axe-core';
import { expect } from 'vitest';

const BLOCKING = new Set(['serious', 'critical']);

/**
 * Runs axe on an element and fails when a violation of impact serious or critical is found. Lower impacts are
 * reported as warnings, so a backlog of moderate findings does not block the pull request.
 */
export async function expectNoSeriousA11yViolations(element: Element): Promise<void> {
  const result = await axe.run(element, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'] },
  });
  const blocking = result.violations.filter((v) => v.impact && BLOCKING.has(v.impact));
  for (const violation of result.violations.filter((v) => !blocking.includes(v))) {
    console.warn(`a11y (${violation.impact}): ${violation.id} - ${violation.help}`);
  }
  const summary = blocking.map((v) => `${v.impact}: ${v.id} (${v.nodes.length} node(s)) - ${v.help}`);
  const rules = blocking.map((v) => v.id).join(', ');
  expect(summary, `serious or critical accessibility violations: ${rules}`).toEqual([]);
}
