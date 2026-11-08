<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { Channel, Series, Session } from '../../api/types';
import { canEditSeries, SESSION_KEY } from '../../auth/session';
import LoadState from '../../components/LoadState.vue';
import { useAsync } from '../../composables/useAsync';
import { calendarApi } from './api';
import {
  berlinDateKey,
  type CalendarTab,
  periodLabel,
  periodRange,
  rangeToInstants,
  shiftAnchor,
} from './dates';
import ListView from './ListView.vue';
import MonthView from './MonthView.vue';
import PlanDialog from './PlanDialog.vue';
import PublicationPanel from './PublicationPanel.vue';
import { useNow } from './now';
import { STATUS_LABELS } from './labels';
import type { Capability, Publication } from './types';
import WeekView from './WeekView.vue';

const TABS: { id: CalendarTab; label: string }[] = [
  { id: 'monat', label: 'Monat' },
  { id: 'woche', label: 'Woche' },
  { id: 'liste', label: 'Liste' },
];

const route = useRoute();
const router = useRouter();
const now = useNow();
const session = inject(SESSION_KEY, computed<Session | null>(() => null));
const canEdit = computed(() => session.value !== null && canEditSeries(session.value.roles));

const view = computed<CalendarTab>(() => {
  const value = route.query.ansicht;
  return value === 'monat' || value === 'liste' ? value : 'woche';
});

function queryText(name: string): string {
  const value = route.query[name];
  return typeof value === 'string' ? value : '';
}

const anchor = ref(berlinDateKey(now()));
const filters = reactive({
  channelId: queryText('kanal'),
  seriesId: queryText('serie'),
  status: queryText('status'),
});
const selectedId = ref<string | null>(null);

const range = computed(() => periodRange(view.value, anchor.value));

// Reference data: the channels, the series (colours and names), and the platform capabilities.
const reference = useAsync(async () => {
  const [channels, series, platforms] = await Promise.all([
    calendarApi.channels(),
    calendarApi.series(),
    calendarApi.platforms(),
  ]);
  return { channels, series, platforms };
});

const channels = computed<Channel[]>(() => reference.data.value?.channels ?? []);
const series = computed<Series[]>(() => reference.data.value?.series ?? []);
const platforms = computed<Record<string, Capability>>(() => reference.data.value?.platforms ?? {});
const seriesById = computed(() => new Map(series.value.map((item) => [item.id, item])));
const colours = computed(() => new Map(series.value.map((item) => [item.id, item.color])));

const loader = useAsync(() =>
  calendarApi.publications({
    ...rangeToInstants(range.value),
    channelId: filters.channelId || undefined,
    seriesId: filters.seriesId || undefined,
    status: filters.status || undefined,
  }),
);
const publications = computed<Publication[]>(() => loader.data.value ?? []);
const selected = computed(() => publications.value.find((item) => item.id === selectedId.value) ?? null);

function reload(): void {
  void loader.run();
}

onMounted(() => {
  void reference.run();
});

watch(
  () => [range.value.fromKey, range.value.toKey, filters.channelId, filters.seriesId, filters.status],
  reload,
  { immediate: true },
);

function setView(tab: CalendarTab): void {
  void router.replace({ query: { ...route.query, ansicht: tab } });
}

function move(direction: -1 | 1): void {
  anchor.value = shiftAnchor(view.value, anchor.value, direction);
}

function today(): void {
  anchor.value = berlinDateKey(now());
}

function openDay(day: string): void {
  anchor.value = day;
  setView('woche');
}

function select(publication: Publication): void {
  selectedId.value = publication.id;
}

function onDeleted(): void {
  selectedId.value = null;
  reload();
}

function onChanged(): void {
  reload();
}

// The plan dialog: opened by the button (editors) or by the route query ?plan=<assetId> (editors only).
const planOpen = ref(false);
const planPreselect = ref<string | null>(null);
const clips = useAsync(() => calendarApi.renderedClips());
const planned = ref<string | null>(null);

function openPlan(assetId: string | null): void {
  if (!canEdit.value) {
    return;
  }
  planPreselect.value = assetId;
  planOpen.value = true;
  void clips.run();
}

function closePlan(): void {
  planOpen.value = false;
  if (typeof route.query.plan === 'string') {
    const query = { ...route.query };
    delete query.plan;
    void router.replace({ query });
  }
}

function onPlanned(created: Publication[]): void {
  planned.value = `${created.length} ${created.length === 1 ? 'Veröffentlichung' : 'Veröffentlichungen'} eingeplant.`;
  closePlan();
  reload();
}

watch(
  () => route.query.plan,
  (value) => {
    if (typeof value === 'string' && value !== '') {
      openPlan(value);
    }
  },
  { immediate: true },
);

const clipList = computed(() => clips.data.value ?? []);

const periodText = computed(() => periodLabel(view.value, anchor.value));
const statusOptions = Object.entries(STATUS_LABELS) as [keyof typeof STATUS_LABELS, string][];
</script>

<template>
  <div class="page calendar">
    <header class="page-head">
      <div class="head-main">
        <h2>Kalender</h2>
        <p class="page-lead">Geplante und veröffentlichte Clips, alle Zeiten in Berlin.</p>
      </div>
      <md-filled-button v-if="canEdit" type="button" @click="openPlan(null)">Clip einplanen</md-filled-button>
    </header>

    <div class="toolbar">
      <div class="tabs" role="tablist" aria-label="Ansicht">
        <button
          v-for="tab in TABS"
          :key="tab.id"
          type="button"
          role="tab"
          class="tab"
          :aria-selected="view === tab.id"
          :class="{ active: view === tab.id }"
          @click="setView(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="nav">
        <md-text-button type="button" @click="move(-1)">Zurück</md-text-button>
        <md-outlined-button type="button" @click="today">Heute</md-outlined-button>
        <md-text-button type="button" @click="move(1)">Weiter</md-text-button>
        <span class="period" aria-live="polite">{{ periodText }}</span>
      </div>
    </div>

    <div class="filters">
      <label>
        <span>Kanal</span>
        <select v-model="filters.channelId" name="kanal">
          <option value="">Alle Kanäle</option>
          <option v-for="channel in channels" :key="channel.id" :value="channel.id">{{ channel.name }}</option>
        </select>
      </label>
      <label>
        <span>Serie</span>
        <select v-model="filters.seriesId" name="serie">
          <option value="">Alle Serien</option>
          <option v-for="item in series" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>
      <label>
        <span>Status</span>
        <select v-model="filters.status" name="status">
          <option value="">Alle Status</option>
          <option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
    </div>

    <p v-if="planned" class="notice" role="status">{{ planned }}</p>

    <LoadState :loading="loader.loading.value" :error="loader.error.value">
      <div class="layout" :class="{ 'with-panel': selected }">
        <div class="main">
          <MonthView
            v-if="view === 'monat'"
            :publications="publications"
            :anchor-key="anchor"
            :colours="colours"
            :selected-id="selectedId"
            @select="select"
            @open-day="openDay"
          />
          <WeekView
            v-else-if="view === 'woche'"
            :publications="publications"
            :anchor-key="anchor"
            :colours="colours"
            :selected-id="selectedId"
            @select="select"
          />
          <ListView
            v-else
            :publications="publications"
            :colours="colours"
            :selected-id="selectedId"
            @select="select"
          />
        </div>
        <PublicationPanel
          v-if="selected"
          :publication="selected"
          :series="selected.seriesId ? seriesById.get(selected.seriesId) ?? null : null"
          :channel="channels.find((item) => item.id === selected?.channelId)"
          :capability="platforms[selected.platform]"
          :can-edit="canEdit"
          @close="selectedId = null"
          @changed="onChanged"
          @deleted="onDeleted"
        />
      </div>
    </LoadState>

    <PlanDialog
      :open="planOpen"
      :clips="clipList"
      :series="series"
      :channels="channels"
      :platforms="platforms"
      :preselect-asset-id="planPreselect"
      @close="closePlan"
      @planned="onPlanned"
    />
  </div>
</template>

<style scoped>
.head-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-head {
  align-items: flex-start;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
}

.tab {
  padding: 6px 16px;
  border: 0;
  border-radius: 999px;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.tab.active {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.period {
  margin-left: 8px;
  font-weight: 600;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 16px 0;
}

.filters label {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: var(--app-muted);
}

.filters select {
  font: inherit;
  min-width: 180px;
  padding: 8px 10px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 8px;
  background: var(--app-card);
  color: inherit;
}

.notice {
  color: var(--md-sys-color-primary);
}

.layout {
  display: grid;
  gap: 16px;
  align-items: start;
}

@media (min-width: 1000px) {
  .layout.with-panel {
    grid-template-columns: minmax(0, 1fr) minmax(320px, 400px);
  }
}

.main {
  min-width: 0;
}

</style>
