// Typed calls of the Channels and Platforms endpoints (contracts/openapi/menura.yaml). The types are aliases so that
// rows built from them satisfy the shared table's row type.
import type { Fetcher } from '../auth/session';
import { ApiError, apiRequest } from './client';
import type { ChannelQuota } from './posts';

export type ChannelState = 'connected' | 'needs_reconnection' | 'disconnected';
export type AuditStatusValue = 'unknown' | 'unaudited' | 'audited';
export type Revocation = 'revoked' | 'not_supported' | 'failed';

export type AuditView = {
  status: AuditStatusValue;
  displayText: string;
  restrictions: string[];
  decidedAt: string | null;
  source: string | null;
};

export type QuotaView = {
  used: number | null;
  limit: number | null;
  resetAt: string | null;
  warning: boolean;
  delay: { reason: string; nextAttemptAt: string } | null;
};

export type Channel = {
  id: string;
  platform: string;
  displayName: string;
  platformAccountId: string;
  state: ChannelState;
  stateReason: string | null;
  features: string[];
  grantedScopes: string[];
  missingScopes: string[];
  grantRevocation: Revocation | null;
  createdAt: string;
  updatedAt: string;
  quota: QuotaView;
  audit: AuditView;
};

export type Capabilities = {
  publishing: boolean;
  nativeScheduling: boolean;
  listing: boolean;
  metrics: boolean;
  revocation: boolean;
};

export type Platform = {
  platform: string;
  displayName: string;
  capabilities: Capabilities;
  features: { name: string; scopes: string[] }[];
  audit: AuditView;
};

export type ChannelPage = { items: Channel[]; nextCursor: string | null };

const PAGE_SIZE = 100;

const NO_CAPABILITIES: Capabilities = {
  publishing: false,
  nativeScheduling: false,
  listing: false,
  metrics: false,
  revocation: false,
};

export async function listPlatforms(fetcher?: Fetcher): Promise<Platform[]> {
  return ((await apiRequest<Platform[]>('/platforms', {}, fetcher)) ?? []).map(platformOf);
}

/** Records the audit status of a platform app (administrators only). The source is omitted when it is empty. */
export async function recordAuditStatus(
  platform: string,
  request: { status: AuditStatusValue; source?: string },
  fetcher?: Fetcher,
): Promise<AuditView> {
  const body = request.source ? { status: request.status, source: request.source } : { status: request.status };
  const recorded = requireBody(
    await apiRequest<AuditView>(`/platforms/${encodeURIComponent(platform)}/audit-status`, { method: 'PUT', body }, fetcher),
  );
  return auditOf(recorded);
}

/** Every channel, following the cursor. The loop stops when the server repeats a cursor, so it cannot hang. */
export async function listChannels(fetcher?: Fetcher): Promise<Channel[]> {
  const channels: Channel[] = [];
  const seen = new Set<string>();
  let cursor: string | null = null;
  do {
    const query: string = `?limit=${PAGE_SIZE}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const page: ChannelPage | undefined = await apiRequest<ChannelPage>(`/channels${query}`, {}, fetcher);
    if (!page) break;
    channels.push(...(page.items ?? []).map(channelOf));
    cursor = page.nextCursor ?? null;
    if (cursor !== null) {
      if (seen.has(cursor)) break;
      seen.add(cursor);
    }
  } while (cursor !== null);
  return channels;
}

/** Starts a new connection and returns the platform's consent URL, which the browser is sent to. */
export async function startConnection(platform: string, fetcher?: Fetcher): Promise<string> {
  const response = await apiRequest<{ authorizationUrl: string }>(
    '/channels/connections',
    { method: 'POST', body: { platform } },
    fetcher,
  );
  return consentUrl(response);
}

/** Starts a reconnection of a channel through the platform's consent; the channel keeps its identity and history. */
export async function startReconnection(channelId: string, fetcher?: Fetcher): Promise<string> {
  const response = await apiRequest<{ authorizationUrl: string }>(
    `/channels/${encodeURIComponent(channelId)}/consent`,
    { method: 'POST', body: {} },
    fetcher,
  );
  return consentUrl(response);
}

/** Disconnects a channel and returns what the platform did with the grant. The stored tokens are gone in every case. */
export async function disconnectChannel(channelId: string, fetcher?: Fetcher): Promise<Revocation> {
  const response = await apiRequest<{ revocation: Revocation }>(
    `/channels/${encodeURIComponent(channelId)}`,
    { method: 'DELETE' },
    fetcher,
  );
  return requireBody(response).revocation;
}

/** Only http and https consent URLs are accepted; any other address is never navigated to. */
export function consentUrl(response: { authorizationUrl?: unknown } | undefined): string {
  const value = response?.authorizationUrl;
  if (typeof value !== 'string') {
    throw new Error('The platform did not return a consent address.');
  }
  const protocol = new URL(value).protocol;
  if (protocol !== 'https:' && protocol !== 'http:') {
    throw new Error('The platform returned a consent address that is not a web address.');
  }
  return value;
}

// The server leaves out null and empty members of a response. The functions below restore them, so the rest of the
// web app can rely on every field of these types being present.
function auditOf(raw: Partial<AuditView>): AuditView {
  return {
    status: raw.status ?? 'unknown',
    displayText: raw.displayText ?? '',
    restrictions: raw.restrictions ?? [],
    decidedAt: raw.decidedAt ?? null,
    source: raw.source ?? null,
  };
}

function quotaOf(raw: Partial<QuotaView> | undefined): QuotaView {
  return {
    used: raw?.used ?? null,
    limit: raw?.limit ?? null,
    resetAt: raw?.resetAt ?? null,
    warning: raw?.warning ?? false,
    delay: raw?.delay ?? null,
  };
}

function channelOf(raw: Channel): Channel {
  return {
    ...raw,
    stateReason: raw.stateReason ?? null,
    features: raw.features ?? [],
    grantedScopes: raw.grantedScopes ?? [],
    missingScopes: raw.missingScopes ?? [],
    grantRevocation: raw.grantRevocation ?? null,
    quota: quotaOf(raw.quota),
    audit: auditOf(raw.audit ?? {}),
  };
}

function platformOf(raw: Platform): Platform {
  return {
    ...raw,
    capabilities: { ...NO_CAPABILITIES, ...raw.capabilities },
    features: (raw.features ?? []).map((feature) => ({ name: feature.name, scopes: feature.scopes ?? [] })),
    audit: auditOf(raw.audit ?? {}),
  };
}

function requireBody<T>(body: T | undefined): T {
  if (body === undefined) {
    throw new Error('The API answered without a body.');
  }
  return body;
}

/**
 * The remaining daily quota of a YouTube channel. A platform without a daily quota answers 404, which gives null; any other
 * failure is thrown, so the caller can show that the quota is unknown.
 */
export async function fetchChannelQuota(channelId: string, fetcher?: Fetcher): Promise<ChannelQuota | null> {
  try {
    return (await apiRequest<ChannelQuota>(`/channels/${encodeURIComponent(channelId)}/quota`, {}, fetcher)) ?? null;
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 404) {
      return null;
    }
    throw failure;
  }
}
