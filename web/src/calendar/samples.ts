// Sample calendar data for the stories and the tests. It is data only: no story is registered from this file.
import type { CalendarItem, DryRunProblem, PostRevision } from '../api/posts';

export const CHANNEL_ID = 'c0ffee00-0000-4000-8000-000000000001';
export const SECOND_CHANNEL_ID = 'c0ffee00-0000-4000-8000-000000000002';
export const SERIES_ID = 'a1b2c3d4-0000-4000-8000-000000000001';
export const ASSET_ID = 'e1f2a3b4-0000-4000-8000-000000000001';

export function sampleItem(overrides: Partial<CalendarItem> = {}): CalendarItem {
  return {
    id: 'p0000000-0000-4000-8000-000000000001',
    channelId: CHANNEL_ID,
    channelName: 'OneLiteFeather',
    platform: 'youtube',
    channelState: 'connected',
    assetId: ASSET_ID,
    coverAssetId: null,
    seriesId: SERIES_ID,
    sourcePostId: null,
    authorId: 'u0000000-0000-4000-8000-000000000001',
    status: 'approved',
    title: 'Kubernetes in 30 Sekunden',
    description: '',
    hashtags: ['#kubernetes30s'],
    visibility: 'private',
    playlistId: null,
    scheduledAt: '2026-11-09T18:00:00Z',
    handedOverAt: null,
    approvedBy: null,
    approvedAt: null,
    publishedAt: null,
    url: null,
    externalId: null,
    failureReason: null,
    attempts: 0,
    nextAttemptAt: null,
    revision: 1,
    steps: [],
    attemptHistory: [],
    problems: [],
    createdAt: '2026-11-02T10:00:00Z',
    updatedAt: '2026-11-02T10:00:00Z',
    startsAt: '2026-11-09T17:00:00Z',
    localDate: '2026-11-09',
    localTime: '18:00',
    madeForKids: null,
    visibilityDeclared: false,
    plannedShort: false,
    appliedVisibility: null,
    visibilityProblem: null,
    ...overrides,
  };
}

export const TEXT_PROBLEM: DryRunProblem = {
  code: 'text_too_long',
  severity: 'error',
  message: 'Der Text ist für YouTube zu lang (höchstens 5000 Zeichen).',
  action: null,
};

export const RECONNECT_PROBLEM: DryRunProblem = {
  code: 'connection_needs_reconnection',
  severity: 'error',
  message: 'Die Verbindung zu OneLiteFeather ist abgelaufen.',
  action: 'reconnect',
};

export const COLLISION_PROBLEM: DryRunProblem = {
  code: 'slot_collision',
  severity: 'warning',
  message: 'Ein anderer Beitrag liegt auf demselben Kanal zur selben Zeit.',
  action: null,
};

export function sampleRevision(overrides: Partial<PostRevision> = {}): PostRevision {
  return {
    id: 'r0000000-0000-4000-8000-000000000001',
    postId: 'p0000000-0000-4000-8000-000000000001',
    authorId: 'u0000000-0000-4000-8000-000000000002',
    rationale: 'Am Freitagabend erreicht der Kanal mehr Zuschauer.',
    status: 'pending',
    title: null,
    description: null,
    hashtags: null,
    coverAssetId: null,
    scheduledAt: '2026-11-13T18:00:00Z',
    channelId: null,
    decidedBy: null,
    decidedAt: null,
    createdAt: '2026-11-08T09:00:00Z',
    ...overrides,
  };
}
