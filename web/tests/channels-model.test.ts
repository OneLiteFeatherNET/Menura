import { describe, expect, it } from 'vitest';
import { ApiError } from '../src/api/client';
import type { Channel, Platform } from '../src/api/channels';
import { connectedChannel, reconnectChannel, twitch, youtube } from '../src/features/channels/channels.fixtures';
import {
  canDisconnect,
  canReconnect,
  channelRows,
  connectionNoticeText,
  connectionPermissions,
  describeError,
  parseConnectionNotice,
  platformRows,
  quotaText,
  restrictionLabel,
  scopesText,
} from '../src/features/channels/model';

describe('connectionPermissions', () => {
  it('lets a publisher and an administrator start connections', () => {
    expect(connectionPermissions(['PUBLISHER']).canConnect).toBe(true);
    expect(connectionPermissions(['ADMIN']).canConnect).toBe(true);
  });

  it('lets only an administrator record an audit status', () => {
    expect(connectionPermissions(['ADMIN']).canAdminister).toBe(true);
    expect(connectionPermissions(['PUBLISHER']).canAdminister).toBe(false);
  });

  it('gives a viewer, an editor and an operator no action', () => {
    for (const roles of [['VIEWER'], ['EDITOR'], ['OPERATOR'], []]) {
      expect(connectionPermissions(roles), `roles ${roles.join(',') || 'none'}`).toEqual({
        canConnect: false,
        canAdminister: false,
      });
    }
  });
});

describe('reconnect and disconnect eligibility', () => {
  it('offers a reconnection only when the grant needs one', () => {
    expect(canReconnect(connectedChannel)).toBe(false);
    expect(canReconnect(reconnectChannel)).toBe(true);
  });

  it('offers a disconnect for every channel that still has a grant', () => {
    expect(canDisconnect(connectedChannel)).toBe(true);
    expect(canDisconnect(reconnectChannel)).toBe(true);
    expect(canDisconnect({ state: 'disconnected' })).toBe(false);
  });
});

describe('parseConnectionNotice', () => {
  it('returns null when the page was opened without a consent outcome', () => {
    expect(parseConnectionNotice('')).toBeNull();
    expect(parseConnectionNotice('?other=1')).toBeNull();
  });

  it('reads the outcome, the channel and the reason of a consent', () => {
    expect(parseConnectionNotice('?connection=refused&reason=access%20denied')).toEqual({
      kind: 'refused',
      channelId: null,
      reason: 'access denied',
    });
    expect(parseConnectionNotice('?connection=connected&channel=abc')).toEqual({
      kind: 'connected',
      channelId: 'abc',
      reason: null,
    });
  });

  it('treats an outcome it does not know as unknown', () => {
    expect(parseConnectionNotice('?connection=exploded')?.kind).toBe('unknown');
  });
});

describe('connectionNoticeText', () => {
  it('names the reason of a refused consent', () => {
    const text = connectionNoticeText({ kind: 'refused', channelId: null, reason: 'access denied' });

    expect(text).toContain('access denied');
  });

  it('tells a reconnection with another account to connect a new channel', () => {
    const text = connectionNoticeText({ kind: 'account_mismatch', channelId: 'abc', reason: null });

    expect(text).toContain('neuen Kanal');
  });
});

describe('describeError', () => {
  it('maps a refused role to a German sentence', () => {
    expect(describeError(new ApiError(403, 'Forbidden', 'Role required.'))).toBe(
      'Ihre Rolle darf diese Aktion nicht ausführen.',
    );
  });

  it('never shows the server detail of an unmapped failure', () => {
    const text = describeError(new ApiError(500, 'Internal', 'database password in detail'));

    expect(text).toBe('Die Aktion ist fehlgeschlagen.');
    expect(text).not.toContain('password');
  });

  it('gives a generic sentence for a failure that is not an API error', () => {
    expect(describeError(new TypeError('network'))).toBe('Die Aktion ist fehlgeschlagen.');
  });
});

describe('quotaText', () => {
  it('says so when the platform reported no quota', () => {
    expect(quotaText({ used: null, limit: null, resetAt: null, warning: false, delay: null })).toBe('Nicht gemeldet');
  });

  it('shows the used share and flags a nearly used quota', () => {
    expect(quotaText({ used: 9500, limit: 10000, resetAt: null, warning: true, delay: null })).toBe(
      '9500 von 10000 · Warnung: fast aufgebraucht',
    );
  });

  it('shows the delay with its reason and the next attempt', () => {
    const text = quotaText({
      used: null,
      limit: null,
      resetAt: null,
      warning: false,
      delay: { reason: 'rate limit', nextAttemptAt: '2026-11-08T10:00:00Z' },
    });

    expect(text).toContain('Verzögert (rate limit) bis');
  });
});

describe('scopesText', () => {
  it('lists the granted scopes and flags the missing ones', () => {
    const channel = { grantedScopes: ['a.read'], missingScopes: ['b.write'] } as Channel;

    expect(scopesText(channel)).toBe('a.read · Fehlend: b.write');
  });

  it('says so when nothing was granted', () => {
    expect(scopesText({ grantedScopes: [], missingScopes: [] })).toBe('Keine');
  });
});

describe('restrictionLabel', () => {
  it('shows the known restriction in German', () => {
    expect(restrictionLabel('public visibility not available')).toBe('Öffentliche Sichtbarkeit nicht verfügbar');
  });

  it('shows an unknown restriction as the server sent it', () => {
    expect(restrictionLabel('quota exhausted')).toBe('quota exhausted');
  });
});

describe('table rows', () => {
  it('gives each channel a German state label, its tone and the audit text', () => {
    const [row] = channelRows([reconnectChannel]);

    expect(row?.stateLabel).toBe('Neu verbinden nötig');
    expect(row?.stateTone).toBe('warning');
    expect(row?.reason).toBe('Die Freigabe wurde beim Anbieter entfernt.');
    expect(row?.audit).toBe('Nicht geprüft · Öffentliche Sichtbarkeit nicht verfügbar');
    expect(row?.channel).toBe(reconnectChannel);
  });

  it('lists the capabilities of a platform and says when it has none', () => {
    const [youtubeRow, twitchRow] = platformRows([youtube, twitch] as Platform[]);

    expect(youtubeRow?.capabilities).toContain('Veröffentlichen');
    expect(youtubeRow?.capabilities).not.toContain('Terminplanung');
    expect(twitchRow?.capabilities).toBe('Freigabe widerrufen');
    expect(twitchRow?.features).toBe('Keine');
    expect(twitchRow?.auditLabel).toBe('Unbekannt');
  });
});
