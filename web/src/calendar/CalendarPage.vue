<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchChannelQuota, listChannels, startReconnection, type Channel } from '../api/channels';
import { ApiError } from '../api/client';
import { listFinishedAssets } from '../api/assets';
import {
  acceptRevision,
  approvePost,
  setYouTubeDeclaration,
  updatePostVisibility,
  fetchCalendar,
  fetchPost,
  feedUrl as buildFeedUrl,
  issueFeedToken,
  listRevisions,
  planEpisodes,
  planPosts,
  rejectRevision,
  revokeFeedToken,
  schedulePost,
  submitPost,
  type CalendarItem,
  type CalendarPage,
  type ChannelQuota,
  type DryRunProblem,
  type Post,
  type PostRevision,
  type PostVisibility,
} from '../api/posts';
import { listSeries, type Series } from '../api/series';
import LoadState from '../components/ui/LoadState.vue';
import PageHeader from '../components/ui/PageHeader.vue';
import type { SelectOption } from '../components/ui/AppSelect.vue';
import CalendarFilters from './CalendarFilters.vue';
import CalendarGrid from './CalendarGrid.vue';
import CalendarList from './CalendarList.vue';
import CalendarToolbar from './CalendarToolbar.vue';
import FeedPanel from './FeedPanel.vue';
import PlanPostDialog from './PlanPostDialog.vue';
import PostPanel from './PostPanel.vue';
import {
  EMPTY_CALENDAR_FILTERS,
  LIST_DAYS,
  actionErrorText,
  calendarQuery,
  canPlan as mayPlan,
  canDeclare as mayDeclare,
  isYouTube,
  listGroups,
  monthWeeks,
  periodLabel,
  postActions,
  shiftPeriod,
  weekDays,
  type CalendarFilters as Filters,
  type CalendarView,
  type PlanSubmit,
  type ScheduleRefusal,
} from './model';

// The content calendar page: the period (month, week or list) of the calendar read, its filters, one post in a side panel,
// the planning dialog and the calendar feed. The server decides every right and every rule; the page only shows the
// actions the role and the status allow, and reports the server's refusal next to the action.
const props = defineProps<{ roles: string[] }>();

const view = ref<CalendarView>('month');
// The day the period is anchored to; undefined means today in the installation zone (the server's default).
const anchor = ref<string | undefined>(undefined);
const filters = ref<Filters>({ ...EMPTY_CALENDAR_FILTERS });
const page = ref<CalendarPage | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
const notice = ref<string | undefined>(undefined);

const channels = ref<Channel[]>([]);
const series = ref<Series[]>([]);
const channelFilterOptions = computed<SelectOption[]>(() => channels.value.map((channel) => ({ value: channel.id, label: `${channel.displayName} (${channel.platform})` })));
const seriesFilterOptions = computed<SelectOption[]>(() => series.value.map((entry) => ({ value: entry.id, label: entry.name })));
const planChannelOptions = computed<SelectOption[]>(() =>
  channels.value.filter((channel) => channel.state === 'connected').map((channel) => ({ value: channel.id, label: channel.displayName })),
);
const planSeriesOptions = computed<SelectOption[]>(() =>
  series.value.filter((entry) => entry.active).map((entry) => ({ value: entry.id, label: entry.name })),
);
const assetOptions = ref<SelectOption[]>([]);

const selectedId = ref<string | null>(null);
const revisions = ref<PostRevision[]>([]);
const revisionsStatus = ref<'loading' | 'error' | 'ready'>('ready');
const panelBusy = ref(false);
const panelNotice = ref<string | undefined>(undefined);
const refusal = ref<ScheduleRefusal | null>(null);
const quota = ref<ChannelQuota | null>(null);
const detail = ref<Post | null>(null);
const declarationError = ref<string | undefined>(undefined);

const planOpen = ref(false);
const planBusy = ref(false);
const planError = ref<string | undefined>(undefined);

const feedAddress = ref<string | null>(null);
const feedBusy = ref(false);
const feedError = ref<string | undefined>(undefined);

const canPlan = computed(() => mayPlan(props.roles) && page.value !== null);
const selectedItem = computed<CalendarItem | null>(() => page.value?.items.find((item) => item.id === selectedId.value) ?? null);
// The panel shows the post as the single-post read gives it: the calendar item does not carry the YouTube declaration, so
// the panel waits for that read and then merges it over the calendar item. Until then no panel is shown.
const panelItem = computed<CalendarItem | null>(() => {
  const item = selectedItem.value;
  if (!item || !detail.value || detail.value.id !== item.id) return null;
  return { ...item, ...detail.value } as CalendarItem;
});
const actions = computed(() => (panelItem.value ? postActions(panelItem.value, props.roles) : null));
const label = computed(() => (page.value ? periodLabel(view.value, page.value.firstDay, page.value.lastDay) : ''));
const weeks = computed(() => {
  if (!page.value) return [];
  return view.value === 'month'
    ? monthWeeks(page.value.firstDay, page.value.lastDay)
    : [weekDays(page.value.firstDay)];
});
const groups = computed(() => (page.value ? listGroups(page.value) : []));

async function load() {
  status.value = 'loading';
  try {
    page.value = await fetchCalendar(
      calendarQuery({ view: view.value, date: anchor.value, days: LIST_DAYS, filters: filters.value }),
    );
    // The period stays anchored to the first day the server answered with, so the next step moves from that window.
    anchor.value = page.value.firstDay;
    status.value = 'ready';
  } catch {
    status.value = 'error';
  }
}

async function loadReferenceData() {
  // The filters and the planning form need the channels and series; a failure leaves them empty instead of blocking.
  try {
    channels.value = await listChannels();
  } catch {
    channels.value = [];
  }
  try {
    series.value = await listSeries();
  } catch {
    series.value = [];
  }
}

function step(direction: -1 | 1) {
  if (!page.value) return;
  anchor.value = shiftPeriod(view.value, page.value.firstDay, direction, LIST_DAYS);
  void load();
}

function today() {
  anchor.value = undefined;
  void load();
}

function changeView(next: CalendarView) {
  view.value = next;
  void load();
}

function changeFilters(next: Filters) {
  filters.value = next;
  void load();
}

async function loadRevisions() {
  if (!selectedId.value) return;
  revisionsStatus.value = 'loading';
  try {
    revisions.value = await listRevisions(selectedId.value);
    revisionsStatus.value = 'ready';
  } catch {
    revisionsStatus.value = 'error';
  }
}

function openPost(id: string) {
  selectedId.value = id;
  refusal.value = null;
  panelNotice.value = undefined;
  declarationError.value = undefined;
  quota.value = null;
  detail.value = null;
  void loadRevisions();
  void loadQuota();
  void loadDetail(id);
}

async function loadDetail(id: string) {
  try {
    const post = await fetchPost(id);
    if (selectedId.value === id) detail.value = post;
  } catch {
    if (selectedId.value === id) detail.value = null;
  }
}

// The remaining quota of a YouTube channel is shown next to its post; any failure leaves it out instead of blocking.
async function loadQuota() {
  const item = selectedItem.value;
  if (!item || !isYouTube(item.platform)) return;
  try {
    quota.value = await fetchChannelQuota(item.channelId);
  } catch {
    quota.value = null;
  }
}

// The declaration is saved in two steps: the visibility (the post records that a person chose it), then the made-for-kids
// answer and the Short flag. The server refuses both once the post is scheduled.
async function saveDeclaration(declaration: { visibility: PostVisibility; madeForKids: boolean; planShort: boolean }) {
  const item = selectedItem.value;
  if (!item) return;
  panelBusy.value = true;
  declarationError.value = undefined;
  panelNotice.value = undefined;
  try {
    await updatePostVisibility(item.id, declaration.visibility);
    await setYouTubeDeclaration(item.id, { madeForKids: declaration.madeForKids, planShort: declaration.planShort });
    panelNotice.value = 'Die Angaben zur Veröffentlichung wurden gespeichert.';
    await load();
    await loadDetail(item.id);
  } catch (failure) {
    declarationError.value = actionErrorText(failure, 'Die Angaben konnten nicht gespeichert werden.');
  } finally {
    panelBusy.value = false;
  }
}

function closePost() {
  selectedId.value = null;
  detail.value = null;
  revisions.value = [];
  refusal.value = null;
  panelNotice.value = undefined;
}

// One action on the selected post. A refused schedule (422) keeps its problems next to the action; any other failure
// shows the German text of its status.
async function runAction(run: (id: string) => Promise<unknown>, done: string) {
  const item = selectedItem.value;
  if (!item) return;
  panelBusy.value = true;
  refusal.value = null;
  panelNotice.value = undefined;
  try {
    await run(item.id);
    panelNotice.value = done;
    await load();
    await loadRevisions();
    await loadDetail(item.id);
  } catch (failure) {
    const problems = failure instanceof ApiError ? (failure.problems as DryRunProblem[]) : [];
    refusal.value = {
      message: actionErrorText(failure, 'Die Aktion ist fehlgeschlagen.'),
      problems,
    };
  } finally {
    panelBusy.value = false;
  }
}

function submitSelected() {
  void runAction((id) => submitPost(id), 'Der Beitrag wurde zur Prüfung gesendet.');
}

function approveSelected() {
  void runAction((id) => approvePost(id), 'Der Beitrag wurde freigegeben.');
}

function scheduleSelected() {
  void runAction((id) => schedulePost(id), 'Der Beitrag ist eingeplant.');
}

async function reconnectSelected() {
  const item = selectedItem.value;
  if (!item) return;
  panelBusy.value = true;
  try {
    // The platform's consent is the only way to reconnect; the browser is sent there, and comes back to the calendar.
    window.location.assign(await startReconnection(item.channelId));
  } catch (failure) {
    refusal.value = { message: actionErrorText(failure, 'Die Verbindung konnte nicht neu gestartet werden.'), problems: [] };
    panelBusy.value = false;
  }
}

function decideRevision(decision: (postId: string, revisionId: string) => Promise<unknown>, done: string) {
  return (revisionId: string) => {
    if (!selectedId.value) return;
    const postId = selectedId.value;
    void runAction(() => decision(postId, revisionId), done);
  };
}

const acceptSelectedRevision = decideRevision((postId, revisionId) => acceptRevision(postId, revisionId), 'Der Vorschlag wurde übernommen.');
const rejectSelectedRevision = decideRevision((postId, revisionId) => rejectRevision(postId, revisionId), 'Der Vorschlag wurde abgelehnt.');

async function openPlan() {
  planError.value = undefined;
  planOpen.value = true;
  try {
    const assets = await listFinishedAssets();
    assetOptions.value = assets.map((asset) => ({ value: asset.id, label: `${asset.kind === 'video' ? 'Video' : 'Bild'} · ${asset.format} · ${asset.id.slice(0, 8)}` }));
  } catch {
    assetOptions.value = [];
    planError.value = 'Die fertigen Clips konnten nicht geladen werden.';
  }
}

function channelName(id: string): string {
  return channels.value.find((channel) => channel.id === id)?.displayName ?? 'Ein Kanal';
}

async function plan(submit: PlanSubmit) {
  planBusy.value = true;
  planError.value = undefined;
  try {
    if (submit.kind === 'posts') {
      const result = await planPosts(submit.request);
      const refused = result.refused.map((entry) => channelName(entry.channelId));
      notice.value = refused.length > 0
        ? `${result.posts.length} Entwürfe angelegt. Nicht angelegt: ${refused.join(', ')}.`
        : `${result.posts.length} Entwürfe angelegt.`;
    } else {
      const result = await planEpisodes(submit.seriesId, submit.request);
      notice.value = `${result.posts.length} Folgen angelegt, ${result.skipped.length} Termine übersprungen.`;
    }
    planOpen.value = false;
    await load();
  } catch (failure) {
    planError.value = failure instanceof ApiError && failure.status === 422
      ? 'Die Planung wurde abgelehnt. Prüfen Sie die Angaben.'
      : actionErrorText(failure, 'Die Planung ist fehlgeschlagen.');
  } finally {
    planBusy.value = false;
  }
}

async function issueFeed() {
  feedBusy.value = true;
  feedError.value = undefined;
  try {
    const issued = await issueFeedToken();
    feedAddress.value = buildFeedUrl(issued.token);
  } catch (failure) {
    feedError.value = actionErrorText(failure, 'Die Kalender-Adresse konnte nicht erstellt werden.');
  } finally {
    feedBusy.value = false;
  }
}

async function revokeFeed() {
  feedBusy.value = true;
  feedError.value = undefined;
  try {
    await revokeFeedToken();
    feedAddress.value = null;
  } catch (failure) {
    feedError.value = actionErrorText(failure, 'Die Kalender-Adresse konnte nicht gesperrt werden.');
  } finally {
    feedBusy.value = false;
  }
}

onMounted(() => {
  void load();
  void loadReferenceData();
});
</script>

<template>
  <section class="calendar-page">
    <PageHeader title="Kalender" subtitle="Geplante und veröffentlichte Beiträge je Kanal, mit ihren Prüfungen." />

    <p v-if="notice" class="calendar-page__notice" role="status">{{ notice }}</p>

    <CalendarToolbar
      :view="view"
      :label="label"
      :can-plan="canPlan"
      @update:view="changeView"
      @previous="step(-1)"
      @next="step(1)"
      @today="today"
      @plan="openPlan"
    />

    <CalendarFilters
      :model-value="filters"
      :channels="channelFilterOptions"
      :series="seriesFilterOptions"
      @update:model-value="changeFilters"
    />

    <LoadState :status="status" error-message="Der Kalender konnte nicht geladen werden." @retry="load">
      <p v-if="page?.truncated" class="calendar-page__notice" role="alert">
        Der Zeitraum enthält mehr Beiträge, als angezeigt werden können. Wählen Sie einen kürzeren Zeitraum oder einen Filter.
      </p>
      <div class="calendar-page__body">
        <div class="calendar-page__main">
          <CalendarList
            v-if="view === 'list'"
            :groups="groups"
            :selected-id="selectedId"
            @select="openPost"
          />
          <CalendarGrid
            v-else-if="page"
            :weeks="weeks"
            :items="page.items"
            :selected-id="selectedId"
            @select="openPost"
          />
        </div>
        <aside class="calendar-page__side" aria-label="Beitrag und Kalender-Abo">
          <PostPanel
            v-if="panelItem && actions"
            :item="panelItem"
            :actions="actions"
            :busy="panelBusy"
            :refusal="refusal"
            :revisions="revisions"
            :revisions-status="revisionsStatus"
            :notice="panelNotice"
            @close="closePost"
            @submit="submitSelected"
            @approve="approveSelected"
            @schedule="scheduleSelected"
            @reconnect="reconnectSelected"
            @accept="acceptSelectedRevision"
            @reject="rejectSelectedRevision"
            @retry-revisions="loadRevisions"
            @declare="saveDeclaration"
            :quota="quota"
            :time-zone="page?.timeZone ?? 'Europe/Berlin'"
            :can-declare="mayDeclare(panelItem.status, props.roles)"
            :declaration-error="declarationError"
          />
          <FeedPanel :feed-url="feedAddress" :busy="feedBusy" :error="feedError" @issue="issueFeed" @revoke="revokeFeed" />
        </aside>
      </div>
    </LoadState>

    <PlanPostDialog
      :open="planOpen"
      :assets="assetOptions"
      :channels="planChannelOptions"
      :series="planSeriesOptions"
      :time-zone="page?.timeZone ?? ''"
      :busy="planBusy"
      :error="planError"
      @close="planOpen = false"
      @submit="plan"
    />
  </section>
</template>

<style scoped>
.calendar-page {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.calendar-page__notice {
  margin: 0;
  font-size: 14px;
}

.calendar-page__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
}

.calendar-page__main,
.calendar-page__side {
  display: grid;
  gap: 16px;
  min-width: 0;
  align-content: start;
}

@media (min-width: 1000px) {
  .calendar-page__body {
    grid-template-columns: minmax(0, 1fr) minmax(300px, 380px);
  }
}
</style>
