// Pure rules of the channels page: labels, what each role may do, the outcome of a consent, and the words shown for
// errors. Nothing here touches the network or the DOM, so the rules are tested without a browser.
import { ApiError } from '../../api/client';
import type { AuditStatusValue, Capabilities, Channel, ChannelState, Platform, QuotaView, Revocation } from '../../api/channels';

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'error';

const STATE_LABELS: Record<ChannelState, string> = {
  connected: 'Verbunden',
  needs_reconnection: 'Neu verbinden nötig',
  disconnected: 'Getrennt',
};

const STATE_TONES: Record<ChannelState, Tone> = {
  connected: 'success',
  needs_reconnection: 'warning',
  disconnected: 'neutral',
};

const AUDIT_LABELS: Record<AuditStatusValue, string> = {
  unknown: 'Unbekannt',
  unaudited: 'Nicht geprüft',
  audited: 'Geprüft',
};

const AUDIT_TONES: Record<AuditStatusValue, Tone> = {
  unknown: 'neutral',
  unaudited: 'warning',
  audited: 'success',
};

/** The server names restrictions in English; the known ones are shown in German, an unknown one is shown as sent. */
const RESTRICTION_LABELS: Record<string, string> = {
  'public visibility not available': 'Öffentliche Sichtbarkeit nicht verfügbar',
};

const CAPABILITY_LABELS: Record<keyof Capabilities, string> = {
  publishing: 'Veröffentlichen',
  nativeScheduling: 'Terminplanung beim Anbieter',
  listing: 'Vorhandene Beiträge lesen',
  metrics: 'Kennzahlen lesen',
  revocation: 'Freigabe widerrufen',
};

export function channelStateLabel(state: ChannelState): string {
  return STATE_LABELS[state];
}

export function channelStateTone(state: ChannelState): Tone {
  return STATE_TONES[state];
}

export function auditLabel(status: AuditStatusValue): string {
  return AUDIT_LABELS[status];
}

export function auditTone(status: AuditStatusValue): Tone {
  return AUDIT_TONES[status];
}

export function restrictionLabel(restriction: string): string {
  return RESTRICTION_LABELS[restriction] ?? restriction;
}

export function capabilityLabels(capabilities: Capabilities): string[] {
  return (Object.keys(CAPABILITY_LABELS) as (keyof Capabilities)[])
    .filter((key) => capabilities[key])
    .map((key) => CAPABILITY_LABELS[key]);
}

/** Who may start or end a connection, and who may record an app's audit status, as the server's role checks decide. */
export interface ConnectionPermissions {
  canConnect: boolean;
  canAdminister: boolean;
}

export function connectionPermissions(roles: readonly string[]): ConnectionPermissions {
  return {
    canConnect: roles.includes('PUBLISHER') || roles.includes('ADMIN'),
    canAdminister: roles.includes('ADMIN'),
  };
}

/** A reconnection is offered only where the grant is dead; a connected channel needs none. */
export function canReconnect(channel: Pick<Channel, 'state'>): boolean {
  return channel.state === 'needs_reconnection';
}

/** A disconnected channel has nothing left to disconnect. */
export function canDisconnect(channel: Pick<Channel, 'state'>): boolean {
  return channel.state !== 'disconnected';
}

export function quotaText(quota: QuotaView): string {
  const parts: string[] = [];
  if (quota.used === null && quota.limit === null) {
    parts.push('Nicht gemeldet');
  } else {
    parts.push(`${quota.used ?? '–'} von ${quota.limit ?? '–'}`);
  }
  if (quota.warning) {
    parts.push('Warnung: fast aufgebraucht');
  }
  if (quota.delay) {
    parts.push(`Verzögert (${quota.delay.reason}) bis ${formatDateTime(quota.delay.nextAttemptAt)}`);
  }
  return parts.join(' · ');
}

export function scopesText(channel: Pick<Channel, 'grantedScopes' | 'missingScopes'>): string {
  const granted = channel.grantedScopes.length ? channel.grantedScopes.join(', ') : 'Keine';
  if (channel.missingScopes.length === 0) {
    return granted;
  }
  return `${granted} · Fehlend: ${channel.missingScopes.join(', ')}`;
}

export function revocationText(revocation: Revocation): string {
  switch (revocation) {
    case 'revoked':
      return 'Der Anbieter hat die Freigabe widerrufen.';
    case 'not_supported':
      return 'Der Anbieter unterstützt keinen Widerruf. Die Freigabe bitte in den Kontoeinstellungen des Anbieters entfernen.';
    case 'failed':
      return 'Der Widerruf beim Anbieter ist fehlgeschlagen. Die Freigabe könnte noch bestehen; bitte in den Kontoeinstellungen des Anbieters entfernen.';
  }
}

/** The outcome the consent callback reports in the query of the channels page. */
export type ConnectionNotice =
  | { kind: 'connected' | 'already_connected' | 'refused' | 'account_mismatch' | 'unknown'; channelId: string | null; reason: string | null };

const CONSENT_KINDS: Record<string, ConnectionNotice['kind']> = {
  connected: 'connected',
  already_connected: 'already_connected',
  refused: 'refused',
  account_mismatch: 'account_mismatch',
};

/** Reads the outcome of a consent from the query string; null when the page was opened without one. */
export function parseConnectionNotice(search: string): ConnectionNotice | null {
  const params = new URLSearchParams(search);
  const connection = params.get('connection');
  if (connection === null) {
    return null;
  }
  return {
    kind: CONSENT_KINDS[connection] ?? 'unknown',
    channelId: params.get('channel'),
    reason: params.get('reason'),
  };
}

export function connectionNoticeText(notice: ConnectionNotice): string {
  switch (notice.kind) {
    case 'connected':
      return 'Der Kanal ist verbunden.';
    case 'already_connected':
      return 'Dieses Konto ist bereits verbunden. Der vorhandene Kanal wurde übernommen.';
    case 'refused':
      return notice.reason ? `Die Verbindung wurde nicht hergestellt: ${notice.reason}` : 'Die Verbindung wurde nicht hergestellt.';
    case 'account_mismatch':
      return 'Die Einwilligung gehört zu einem anderen Konto. Verbinden Sie es bitte als neuen Kanal.';
    case 'unknown':
      return 'Die Verbindung konnte nicht abgeschlossen werden.';
  }
}

/** The German text for a failed request. The status decides the text; the server's English detail is never shown. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return 'Ihre Anmeldung ist abgelaufen. Bitte melden Sie sich erneut an.';
      case 403:
        return 'Ihre Rolle darf diese Aktion nicht ausführen.';
      case 404:
        return 'Der Kanal wurde nicht gefunden.';
      case 409:
        return 'Die Aktion ist im aktuellen Zustand des Kanals nicht möglich.';
      case 503:
        return 'Der Dienst ist derzeit nicht verfügbar. Bitte versuchen Sie es später erneut.';
      default:
        return 'Die Aktion ist fehlgeschlagen.';
    }
  }
  return 'Die Aktion ist fehlgeschlagen.';
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' });
}

/** Table rows for the channel list. Every cell is text; the state and the row actions are added by the view. */
export interface ChannelRow {
  [key: string]: string | Channel;
  id: string;
  name: string;
  platform: string;
  stateLabel: string;
  stateTone: Tone;
  reason: string;
  scopes: string;
  quota: string;
  audit: string;
  channel: Channel;
}

export function channelRows(channels: readonly Channel[]): ChannelRow[] {
  return channels.map((channel) => ({
    id: channel.id,
    name: channel.displayName,
    platform: channel.platform,
    stateLabel: channelStateLabel(channel.state),
    stateTone: channelStateTone(channel.state),
    reason: channel.stateReason ?? '',
    scopes: scopesText(channel),
    quota: quotaText(channel.quota),
    audit: [auditLabel(channel.audit.status), ...channel.audit.restrictions.map(restrictionLabel)].join(' · '),
    channel,
  }));
}

/** Table rows for the platform list. */
export interface PlatformRow {
  [key: string]: string | Platform;
  id: string;
  name: string;
  capabilities: string;
  features: string;
  auditLabel: string;
  auditTone: Tone;
  restrictions: string;
  platform: Platform;
}

export function platformRows(platforms: readonly Platform[]): PlatformRow[] {
  return platforms.map((platform) => ({
    id: platform.platform,
    name: platform.displayName,
    capabilities: capabilityLabels(platform.capabilities).join(', ') || 'Keine',
    features: platform.features.map((feature) => feature.name).join(', ') || 'Keine',
    auditLabel: auditLabel(platform.audit.status),
    auditTone: auditTone(platform.audit.status),
    restrictions: platform.audit.restrictions.map(restrictionLabel).join(', ') || 'Keine',
    platform,
  }));
}
