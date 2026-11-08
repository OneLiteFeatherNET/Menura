// Wire types of the pool endpoints (contract "1 Pool"). Field names are fixed by the server.

export type AssetKind = 'raw' | 'clip';
export type AssetStage = 'raw' | 'cut' | 'rendered';
export type TranscriptState = 'none' | 'pending' | 'ready';

export interface Person {
  id: string;
  name: string;
}

export interface Asset {
  id: string;
  kind: AssetKind;
  title: string;
  ownerId: string;
  people: string[];
  tags: string[];
  durationSeconds: number;
  createdAt: string;
  sourceAssetId: string | null;
  seriesId: string | null;
  stage: AssetStage;
  transcript: TranscriptState;
  sizeBytes: number;
}

export interface TranscriptSegment {
  startSeconds: number;
  endSeconds: number;
  personId: string | null;
  text: string;
}

export interface AssetDetail extends Asset {
  transcriptSegments: TranscriptSegment[];
  clips: string[];
}

/** The list filters. Unset keys are not sent. */
export interface AssetFilter {
  kind?: AssetKind;
  ownerId?: string;
  personId?: string;
  tag?: string;
  stage?: AssetStage;
  q?: string;
}

export interface TagsUpdate {
  tags: string[];
  people: string[];
}

export interface NewAsset {
  kind: AssetKind;
  title: string;
  tags: string[];
  people: string[];
  durationSeconds: number;
  seriesId?: string | null;
}
