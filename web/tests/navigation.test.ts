import { describe, expect, it } from 'vitest';
import { AREAS, canOpen, findArea, normalizePath, visibleAreas, type AppArea } from '../src/navigation/areas';

const LABELS = ['Übersicht', 'Pool', 'Serien', 'Brand-Kit', 'Kanäle'];
const ROLES = ['VIEWER', 'EDITOR', 'PUBLISHER', 'OPERATOR', 'ADMIN'];

function labelsFor(roles: string[], areas?: readonly AppArea[]): string[] {
  return visibleAreas(roles, areas).map((area) => area.label);
}

const RESTRICTED: AppArea = {
  path: '/restricted',
  label: 'Eingeschränkt',
  icon: 'M0 0h24v24H0z',
  roles: ['PUBLISHER'],
  exists: true,
};

describe('navigation items per role', () => {
  it.each(ROLES)('shows the existing areas to the role %s, in the order of the navigation', (role) => {
    expect(labelsFor([role]), `the areas visible to ${role}`).toEqual(LABELS);
  });

  it('shows the same items to every signed-in role, because every existing area is open to any signed-in person', () => {
    const byRole = ROLES.map((role) => labelsFor([role]));

    expect(new Set(byRole.map((labels) => labels.join('|'))).size).toBe(1);
  });

  it('shows the areas open to any signed-in person even when the session names no role', () => {
    expect(labelsFor([]), 'the areas visible to a session without roles').toEqual(LABELS);
  });

  it('hides an area whose roles the person does not hold', () => {
    expect(labelsFor(['VIEWER'], [RESTRICTED]), 'a viewer does not see a publisher area').toEqual([]);
  });

  it('shows an area whose role the person holds, among the roles they hold', () => {
    expect(labelsFor(['VIEWER', 'PUBLISHER'], [RESTRICTED])).toEqual(['Eingeschränkt']);
  });

  it('never lists an area whose page does not exist, whatever the role', () => {
    const planned: AppArea = { ...RESTRICTED, path: '/planned', label: 'Geplant', roles: 'any', exists: false };

    expect(labelsFor(['ADMIN'], [planned]), 'an admin does not see a planned area').toEqual([]);
  });

  it('ignores a role name the server does not know', () => {
    expect(labelsFor(['UNKNOWN'], [RESTRICTED])).toEqual([]);
  });

  it('keeps the order of the areas when some are hidden', () => {
    const first: AppArea = { ...RESTRICTED, path: '/first', label: 'Erste', roles: 'any' };
    const hidden: AppArea = { ...RESTRICTED, path: '/hidden', label: 'Versteckt' };
    const last: AppArea = { ...RESTRICTED, path: '/last', label: 'Letzte', roles: 'any' };

    expect(labelsFor(['VIEWER'], [first, hidden, last])).toEqual(['Erste', 'Letzte']);
  });
});

describe('canOpen', () => {
  it('lets any role open an area whose roles are any', () => {
    expect(canOpen({ ...RESTRICTED, roles: 'any' }, [])).toBe(true);
  });

  it('lets a role open an area that names one of its roles', () => {
    expect(canOpen(RESTRICTED, ['ADMIN', 'PUBLISHER'])).toBe(true);
  });

  it('does not let an admin open a publisher area, because a role grants only itself', () => {
    expect(canOpen(RESTRICTED, ['ADMIN'])).toBe(false);
  });
});

describe('area table', () => {
  it('gives every existing area a page component', () => {
    const missing = AREAS.filter((area) => area.exists && !area.component).map((area) => area.path);

    expect(missing, 'areas that exist need a component').toEqual([]);
  });

  it('gives no planned area a page component or a route', () => {
    const planned = AREAS.filter((area) => !area.exists);

    expect(planned.map((area) => area.path)).toEqual(['/kalender', '/analytics']);
    expect(planned.filter((area) => area.component), 'planned areas have no component').toEqual([]);
  });

  it('gives every area a unique path', () => {
    const paths = AREAS.map((area) => area.path);

    expect(new Set(paths).size, 'paths are unique').toBe(paths.length);
  });
});

describe('path lookup', () => {
  it('strips trailing slashes and keeps the root', () => {
    expect(normalizePath('/pool/')).toBe('/pool');
    expect(normalizePath('/')).toBe('/');
    expect(normalizePath('//')).toBe('/');
  });

  it('finds the area of a path with a trailing slash', () => {
    expect(findArea('/brand-kit/')?.label).toBe('Brand-Kit');
  });

  it('finds no area for an unknown path', () => {
    expect(findArea('/unbekannt')).toBeUndefined();
  });
});
