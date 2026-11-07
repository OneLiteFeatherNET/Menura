// Types of the prototype API (see the prototype API contract). All JSON is camelCase.

export type Role = 'VIEWER' | 'EDITOR' | 'ADMIN';

export interface Session {
  subject: string;
  roles: Role[];
}

export type Platform = 'youtube' | 'tiktok' | 'instagram' | 'direct';
export type ChannelPlatform = Exclude<Platform, 'direct'>;
export type Audience = 'players' | 'developers' | 'mixed';

export interface ColorToken {
  name: string;
  light: string;
  dark: string;
}

export interface Mark {
  name: string;
  safeAreaPercent: number;
}

export interface SafeZone {
  topPercent: number;
  bottomPercent: number;
}

export interface SlotDefinition {
  name: string;
  fromSeconds: number | null;
  toSeconds: number | null;
  lastSeconds: number | null;
  x: number;
  y: number;
  w: number;
  h: number;
  params?: string[];
}

export interface Template {
  id: string;
  width: number;
  height: number;
  safeZone: SafeZone;
  slots: SlotDefinition[];
}

export interface BrandKit {
  version: number;
  source: string;
  tokens: ColorToken[];
  marks: Mark[];
  templates: Template[];
}

export interface Channel {
  id: string;
  platform: ChannelPlatform;
  name: string;
  joinHost: string;
  ctaText: { de: string; en: string };
}

export interface Series {
  id: string;
  name: string;
  badge: { de: string; en: string };
  colorToken: string;
  color: string;
  badgeTextColor: string;
  openingFormula: string;
  hashtag: string;
  audience: Audience;
  weekday: string;
  slot: string;
  playlists: Record<string, string>;
  episodes: number;
  active: boolean;
}

/** Body of POST /api/prototype/series: a series without id and episode count. */
export type NewSeries = Omit<Series, 'id' | 'episodes'>;

export interface Post {
  id: string;
  title: string;
  channelId: string;
  platform: ChannelPlatform;
  publishedAt: string;
  hashtags: string[];
  seriesId: string | null;
  noSeries: boolean;
  episode: number | null;
  engagedViews: number;
  /** Null when the platform does not deliver the value. Show "nicht verfügbar", never 0. */
  avgViewPercent: number | null;
  /** Post-level first joins are always estimates. */
  estimatedFirstJoins: number | null;
  inPlaylist: boolean;
}

export interface PostSeriesUpdate {
  seriesId: string | null;
  noSeries?: boolean;
}

export interface Coverage {
  channelId: string;
  week: string;
  shorts: number;
  covered: number;
  share: number;
  flagged: boolean;
}

export interface WeeklyAttribution {
  week: string;
  platform: Platform;
  channelId: string | null;
  firstJoins: number;
}

export interface SeriesComparison {
  seriesId: string;
  name: string;
  audience: Audience;
  posts: number;
  engagedViews: number;
  avgViewPercent: number | null;
  estimatedFirstJoins: number | null;
  estimate: boolean;
}

export interface Warning {
  kind: string;
  message: string;
}

export interface RenderBundle {
  kitVersion: number;
  brandKit: BrandKit;
  series: Series[];
  channels: Channel[];
}

/** RFC 9457 Problem Details as returned by the server. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}
