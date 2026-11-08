import { expect, test as base, type Page } from '@playwright/test';

/** The API host of the e2e server; the web app is served on the baseURL of the Playwright configuration. */
export const API = 'http://localhost:8080';
export const APP = 'http://localhost:4173/';
export const SESSION_COOKIE = '__Host-menura-session';

export const ROLES = ['viewer', 'editor', 'publisher', 'operator', 'admin'] as const;
export type TestRole = (typeof ROLES)[number];

/** The German names the shell shows for each role (see web/src/auth/session.ts). */
export const ROLE_LABELS: Record<TestRole, string> = {
  viewer: 'Betrachter',
  editor: 'Redaktion',
  publisher: 'Veröffentlichung',
  operator: 'Betrieb',
  admin: 'Administration',
};

/**
 * Signs the browser in as a new test person with the chosen role. The test-only sign-in of the server issues the
 * ordinary session cookie and redirects to the web app, so the page afterwards is the real signed-in shell.
 */
export async function signInAs(page: Page, role: TestRole): Promise<void> {
  await page.goto(`${API}/auth/e2e/sign-in?role=${role}`);
}

export const test = base;
export { expect };
