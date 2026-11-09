import { API, SEEDED_CLIP, expect, signInAs, test } from './fixtures';

// The pool of the content library against the real server: the test-only seed registers one finished clip, so the
// listing shows it, and the filters are sent to the API as query parameters. No seeded item carries the tag used below.
test('an editor opens the pool, filters it by tag and resets the filter', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Pool', exact: true }).click();

  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Content-Pool' }).getByText(SEEDED_CLIP)).toBeVisible();

  const tagSearch = page.getByRole('searchbox', { name: 'Tag' });
  const filtered = page.waitForRequest((request) =>
    request.url().startsWith(`${API}/library/items`) && new URL(request.url()).searchParams.get('tag') === 'speedrun');
  await tagSearch.fill('speedrun');
  expect((await filtered).url()).toContain('limit=50');

  await page.getByRole('button', { name: 'Filter zurücksetzen' }).click();
  await expect(tagSearch).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Filter zurücksetzen' })).toHaveCount(0);
});

test('a viewer reads the same pool without any editing control', async ({ page }) => {
  await signInAs(page, 'viewer');
  await page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Pool', exact: true }).click();

  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Entfernen' })).toHaveCount(0);
});
