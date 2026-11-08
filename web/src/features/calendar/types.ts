/** The wire types of the publishing calendar (contract section 2). Field names are fixed by the server. */

export type PublicationStatus = 'planned' | 'uploading' | 'scheduled' | 'published' | 'failed';
export type PublicationMode = 'native' | 'hub';
export type StepName = 'upload' | 'schedule' | 'metadata' | 'publish' | 'metrics';
export type StepState = 'done' | 'running' | 'waiting' | 'failed';

export interface Step {
  name: StepName;
  state: StepState;
  at: string | null;
  detail: string | null;
}

export interface PublicationMetadata {
  title: string;
  description: string;
  tags: string[];
  playlist: string | null;
}

/** A metric snapshot of a published post. Every metric may be null when the platform does not deliver it. */
export interface Snapshot {
  offset: string;
  at: string;
  views: number | null;
  engagedViews: number | null;
  likes: number | null;
  comments: number | null;
  shares: number | null;
}

export interface Publication {
  id: string;
  assetId: string | null;
  channelId: string;
  platform: string;
  seriesId: string | null;
  publishAt: string;
  mode: PublicationMode;
  status: PublicationStatus;
  metadata: PublicationMetadata;
  steps: Step[];
  postId: string | null;
  snapshots: Snapshot[];
}

export interface Capability {
  nativeSchedule: boolean;
  note: string;
}

/** A rendered clip as the plan dialog lists it. */
export interface ClipSummary {
  id: string;
  title: string;
  seriesId: string | null;
  tags: string[];
  stage: string;
  createdAt: string;
}

export interface NewPublication {
  assetId: string;
  channelIds: string[];
  publishAt: string;
}

export interface PublicationUpdate {
  publishAt?: string;
  metadata?: PublicationMetadata;
}

export interface SeriesPlan {
  channelIds: string[];
  count: number;
}
