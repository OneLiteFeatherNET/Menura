// Sample data for the stories and their play functions. Plain objects, no network.
import type { AuditView, Channel, Platform } from '../../api/channels';

export const unauditedAudit: AuditView = {
  status: 'unaudited',
  displayText: 'unaudited: uploads private only',
  restrictions: ['public visibility not available'],
  decidedAt: null,
  source: null,
};

export const auditedAudit: AuditView = {
  status: 'audited',
  displayText: 'audited',
  restrictions: [],
  decidedAt: '2026-10-02T09:00:00Z',
  source: 'Prüfschreiben vom 2. Oktober',
};

export const unknownAudit: AuditView = {
  status: 'unknown',
  displayText: 'unknown',
  restrictions: ['public visibility not available'],
  decidedAt: null,
  source: null,
};

export const youtube: Platform = {
  platform: 'youtube',
  displayName: 'YouTube',
  capabilities: {
    publishing: true,
    nativeScheduling: false,
    listing: true,
    metrics: true,
    revocation: true,
  },
  features: [{ name: 'uploads', scopes: ['https://www.googleapis.com/auth/youtube.upload'] }],
  audit: unauditedAudit,
};

export const twitch: Platform = {
  platform: 'twitch',
  displayName: 'Twitch',
  capabilities: {
    publishing: false,
    nativeScheduling: false,
    listing: false,
    metrics: false,
    revocation: true,
  },
  features: [],
  audit: unknownAudit,
};

const quotaNone = { used: null, limit: null, resetAt: null, warning: false, delay: null };

export const connectedChannel: Channel = {
  id: '6f1c2a3e-0000-4000-8000-000000000001',
  platform: 'youtube',
  displayName: 'Morgenstream',
  platformAccountId: 'UC-example-1',
  state: 'connected',
  stateReason: null,
  features: ['uploads'],
  grantedScopes: ['https://www.googleapis.com/auth/youtube.upload'],
  missingScopes: [],
  grantRevocation: null,
  createdAt: '2026-10-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
  quota: { used: 1200, limit: 10000, resetAt: '2026-11-08T07:00:00Z', warning: false, delay: null },
  audit: unauditedAudit,
};

export const reconnectChannel: Channel = {
  id: '6f1c2a3e-0000-4000-8000-000000000002',
  platform: 'youtube',
  displayName: 'Speedrun-Archiv',
  platformAccountId: 'UC-example-2',
  state: 'needs_reconnection',
  stateReason: 'Die Freigabe wurde beim Anbieter entfernt.',
  features: ['uploads'],
  grantedScopes: ['https://www.googleapis.com/auth/youtube.upload'],
  missingScopes: ['https://www.googleapis.com/auth/youtube.readonly'],
  grantRevocation: null,
  createdAt: '2026-09-01T08:00:00Z',
  updatedAt: '2026-11-02T08:00:00Z',
  quota: quotaNone,
  audit: unauditedAudit,
};
