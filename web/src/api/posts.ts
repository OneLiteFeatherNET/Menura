// The posts, the calendar and the calendar feed token (contracts/openapi/menura.yaml, tags Posts and Calendar). The
// browser sends its session cookie and never reads or stores a token; the one token shown is the feed link the person
// copies, and it is kept only in the page's memory.
import { apiBase, type Fetcher } from '../auth/session';
import { apiRequest } from './client';
import type { ChannelState } from './channels';

export type PostStatus = 'draft' | 'in_review' | 'approved' | 'scheduled' | 'published' | 'failed';
export type PostVisibility = 'private' | 'unlisted' | 'public';

export interface PostStep {
  step: 'upload' | 'schedule' | 'metadata' | 'publish';
  state: 'pending' | 'running' | 'done' | 'failed';
  changedAt: string;
  reason: string | null;
}

export interface PostAttempt {
  attempt: number;
  startedAt: string;
  finishedAt: string | null;
  outcome: 'handed_over' | 'published' | 'retry_scheduled' | 'failed' | 'unknown' | null;
  reason: string | null;
}

/** A problem the schedule dry run found. A warning does not block; an error does. */
export interface DryRunProblem {
  code: string;
  severity: 'error' | 'warning';
  message: string;
  action: 'reconnect' | null;
}

/** One post as the single-post read and the planning answers give it. */
export interface Post {
  id: string;
  channelId: string;
  channelName: string;
  platform: string;
  channelState: ChannelState;
  assetId: string;
  coverAssetId: string | null;
  seriesId: string | null;
  sourcePostId: string | null;
  authorId: string;
  status: PostStatus;
  title: string;
  description: string;
  hashtags: string[];
  visibility: PostVisibility;
  playlistId: string | null;
  scheduledAt: string | null;
  handedOverAt: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  publishedAt: string | null;
  url: string | null;
  externalId: string | null;
  failureReason: string | null;
  attempts: number;
  nextAttemptAt: string | null;
  revision: number;
  steps: PostStep[];
  attemptHistory: PostAttempt[];
  problems: DryRunProblem[];
  createdAt: string;
  updatedAt: string;
}

/** A post on the calendar: its local date and time in the installation zone come from the server. */
export interface CalendarItem extends Post {
  startsAt: string;
  localDate: string;
  localTime: string;
}

export interface CalendarPage {
  timeZone: string;
  view: 'month' | 'week' | 'list';
  firstDay: string;
  lastDay: string;
  from: string;
  to: string;
  truncated: boolean;
  items: CalendarItem[];
}

export interface PostRevision {
  id: string;
  postId: string;
  authorId: string;
  rationale: string;
  status: 'pending' | 'accepted' | 'rejected';
  title: string | null;
  description: string | null;
  hashtags: string[] | null;
  coverAssetId: string | null;
  scheduledAt: string | null;
  channelId: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
}

/** One channel of a plan, with its time. Unset fields are filled by the server (title and text from the draft rules). */
export interface PostPlanItem {
  channelId: string;
  scheduledAt?: string;
  title?: string;
  description?: string;
  hashtags?: string[];
  visibility?: PostVisibility;
}

export interface PostPlanRequest {
  assetId: string;
  coverAssetId?: string;
  seriesId?: string;
  channels: PostPlanItem[];
}

export interface PlanRefusal {
  channelId: string;
  reason: string;
}

export interface PostPlanResult {
  posts: Post[];
  refused: PlanRefusal[];
}

export interface EpisodePlanRequest {
  channelId: string;
  assetId: string;
  coverAssetId?: string;
  count: number;
}

export interface SkippedSlot {
  scheduledAt: string;
  postId: string;
  reason: string;
}

export interface EpisodePlanResult {
  posts: Post[];
  skipped: SkippedSlot[];
}

export interface IssuedFeedToken {
  token: string;
}

/** The calendar window for a query string (view, date and filters). */
export async function fetchCalendar(query: string, fetcher?: Fetcher): Promise<CalendarPage> {
  const page = await apiRequest<CalendarPage>(`/calendar?${query}`, {}, fetcher);
  return page ?? { timeZone: '', view: 'month', firstDay: '', lastDay: '', from: '', to: '', truncated: false, items: [] };
}

/** One post with its steps, attempts and problems. */
export async function fetchPost(id: string, fetcher?: Fetcher): Promise<Post> {
  return (await apiRequest<Post>(`/posts/${encodeURIComponent(id)}`, {}, fetcher)) as Post;
}

/** An editor sends a draft to review. */
export async function submitPost(id: string, fetcher?: Fetcher): Promise<Post> {
  return (await apiRequest<Post>(`/posts/${encodeURIComponent(id)}/submission`, { method: 'POST' }, fetcher)) as Post;
}

/** A publisher approves a post in review. The web application's session is the only accepted credential. */
export async function approvePost(id: string, fetcher?: Fetcher): Promise<Post> {
  return (await apiRequest<Post>(`/posts/${encodeURIComponent(id)}/approval`, { method: 'POST' }, fetcher)) as Post;
}

/**
 * A publisher schedules an approved post. A dry run that finds a blocking problem refuses it with 422; the problems
 * come with the thrown ApiError.
 */
export async function schedulePost(id: string, fetcher?: Fetcher): Promise<Post> {
  return (await apiRequest<Post>(`/posts/${encodeURIComponent(id)}/schedule`, { method: 'POST' }, fetcher)) as Post;
}

/** The revisions of a post, oldest first. */
export async function listRevisions(postId: string, fetcher?: Fetcher): Promise<PostRevision[]> {
  return (await apiRequest<PostRevision[]>(`/posts/${encodeURIComponent(postId)}/revisions`, {}, fetcher)) ?? [];
}

/** A publisher accepts a pending revision; the post changes and returns to review when it was approved. */
export async function acceptRevision(postId: string, revisionId: string, fetcher?: Fetcher): Promise<Post> {
  const path = `/posts/${encodeURIComponent(postId)}/revisions/${encodeURIComponent(revisionId)}/acceptance`;
  return (await apiRequest<Post>(path, { method: 'POST' }, fetcher)) as Post;
}

/** A publisher rejects a pending revision; the post stays unchanged. */
export async function rejectRevision(postId: string, revisionId: string, fetcher?: Fetcher): Promise<PostRevision> {
  const path = `/posts/${encodeURIComponent(postId)}/revisions/${encodeURIComponent(revisionId)}/rejection`;
  return (await apiRequest<PostRevision>(path, { method: 'POST' }, fetcher)) as PostRevision;
}

/** One finished asset planned onto one or more channels; each channel is checked on its own by the server. */
export async function planPosts(request: PostPlanRequest, fetcher?: Fetcher): Promise<PostPlanResult> {
  return (await apiRequest<PostPlanResult>('/post-plans', { method: 'POST', body: request }, fetcher)) as PostPlanResult;
}

/** The next episodes of a series on one channel, in the free slots of the series. */
export async function planEpisodes(seriesId: string, request: EpisodePlanRequest, fetcher?: Fetcher): Promise<EpisodePlanResult> {
  const path = `/series/${encodeURIComponent(seriesId)}/episodes`;
  return (await apiRequest<EpisodePlanResult>(path, { method: 'POST', body: request }, fetcher)) as EpisodePlanResult;
}

/** Issues the person's feed token. It is shown once, and replaces the previous token, which stops working at once. */
export async function issueFeedToken(fetcher?: Fetcher): Promise<IssuedFeedToken> {
  return (await apiRequest<IssuedFeedToken>('/calendar-feed-token', { method: 'POST' }, fetcher)) as IssuedFeedToken;
}

/** Revokes the person's feed token; calendar clients that use it are refused from then on. */
export async function revokeFeedToken(fetcher?: Fetcher): Promise<void> {
  await apiRequest<void>('/calendar-feed-token', { method: 'DELETE' }, fetcher);
}

/** The address of the iCalendar feed for a token. The token is the only credential, so it is encoded as a path segment. */
export function feedUrl(token: string): string {
  return `${apiBase()}/calendar/feed/${encodeURIComponent(token)}`;
}
