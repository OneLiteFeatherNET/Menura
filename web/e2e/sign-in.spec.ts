import { APP, ROLES, ROLE_LABELS, SESSION_COOKIE, expect, signInAs, test } from './fixtures';

for (const role of ROLES) {
  test(`signing in as ${role} shows that role and no other`, async ({ page }) => {
    await signInAs(page, role);

    await expect(page).toHaveURL(APP);
    await expect(page.getByTestId('session-role')).toHaveText(ROLE_LABELS[role]);
    for (const other of ROLES.filter((r) => r !== role)) {
      await expect(page.getByText(ROLE_LABELS[other], { exact: true })).toHaveCount(0);
    }
  });
}

test('the browser holds only the host-only session cookie after sign-in', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();

  const cookies = await page.context().cookies();
  const session = cookies.find((cookie) => cookie.name === SESSION_COOKIE);

  expect(session, 'the __Host- session cookie is set').toBeDefined();
  expect(session?.httpOnly, 'the cookie is not readable from script').toBe(true);
  expect(session?.secure, 'the cookie is Secure').toBe(true);
  expect(session?.sameSite, 'the cookie is SameSite=Strict').toBe('Strict');
});
