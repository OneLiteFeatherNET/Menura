<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import {
  fetchAnalyticsOverview,
  fetchAnalyticsSeries,
  fetchEqualAge,
  type AnalyticsOverview,
  type AnalyticsSeries,
  type EqualAge,
} from '../api/analytics';
import AnalyticsChannelsPanel from '../components/analytics/AnalyticsChannelsPanel.vue';
import AnalyticsOverviewPanel from '../components/analytics/AnalyticsOverviewPanel.vue';
import AnalyticsSeriesPanel from '../components/analytics/AnalyticsSeriesPanel.vue';
import AnalyticsTimePanel from '../components/analytics/AnalyticsTimePanel.vue';
import PageHeader from '../components/ui/PageHeader.vue';
import { DEFAULT_PERIOD_DAYS, dateLabel, periodRange, type EqualAgeChoice } from './format';

// The analytics page: four tabs, each with its own data. The data is loaded when the page opens and again when the
// period or the equal-age choice changes; a late answer of an older request is dropped. The panels only show the data.
type TabKey = 'overview' | 'channels' | 'series' | 'time';
type Status = 'loading' | 'error' | 'ready';

const TABS: { key: TabKey; label: string; testId: string }[] = [
  { key: 'overview', label: 'Überblick', testId: 'analytics-tab-overview' },
  { key: 'channels', label: 'Kanäle', testId: 'analytics-tab-channels' },
  { key: 'series', label: 'Serien', testId: 'analytics-tab-series' },
  { key: 'time', label: 'Uhrzeit/Wochentag', testId: 'analytics-tab-time' },
];

const active = ref<TabKey>('overview');

const days = ref(DEFAULT_PERIOD_DAYS);
const range = ref(periodRange(new Date(), DEFAULT_PERIOD_DAYS));
const rangeLabel = computed(() => `${dateLabel(range.value.from)} bis ${dateLabel(range.value.to)}`);

const overviewStatus = ref<Status>('loading');
const overview = ref<AnalyticsOverview | null>(null);
let overviewRequest = 0;

const seriesStatus = ref<Status>('loading');
const series = ref<AnalyticsSeries | null>(null);
let seriesRequest = 0;

const timeStatus = ref<Status>('loading');
const equalAge = ref<EqualAge | null>(null);
const choice = ref<EqualAgeChoice>({ by: 'hour', offsetHours: 24, metric: 'views' });
let timeRequest = 0;

async function loadOverview() {
  const request = ++overviewRequest;
  overviewStatus.value = 'loading';
  try {
    const data = await fetchAnalyticsOverview(range.value.from, range.value.to);
    if (request === overviewRequest) {
      overview.value = data;
      overviewStatus.value = 'ready';
    }
  } catch {
    if (request === overviewRequest) {
      overviewStatus.value = 'error';
    }
  }
}

async function loadSeries() {
  const request = ++seriesRequest;
  seriesStatus.value = 'loading';
  try {
    const data = await fetchAnalyticsSeries(range.value.from, range.value.to);
    if (request === seriesRequest) {
      series.value = data;
      seriesStatus.value = 'ready';
    }
  } catch {
    if (request === seriesRequest) {
      seriesStatus.value = 'error';
    }
  }
}

async function loadEqualAge() {
  const request = ++timeRequest;
  timeStatus.value = 'loading';
  try {
    const data = await fetchEqualAge({ ...choice.value });
    if (request === timeRequest) {
      equalAge.value = data;
      timeStatus.value = 'ready';
    }
  } catch {
    if (request === timeRequest) {
      timeStatus.value = 'error';
    }
  }
}

function loadPeriod() {
  void loadOverview();
  void loadSeries();
}

function changePeriod(next: number) {
  days.value = next;
  range.value = periodRange(new Date(), next);
  loadPeriod();
}

function changeChoice(next: EqualAgeChoice) {
  choice.value = next;
  void loadEqualAge();
}

function selectTab(key: TabKey) {
  active.value = key;
}

// The arrow keys, Home and End move between the tabs as the tab pattern asks; Enter and Space activate a button.
function onTabKeydown(event: KeyboardEvent) {
  const index = TABS.findIndex((tab) => tab.key === active.value);
  let target: number | null = null;
  if (event.key === 'ArrowRight') {
    target = (index + 1) % TABS.length;
  } else if (event.key === 'ArrowLeft') {
    target = (index - 1 + TABS.length) % TABS.length;
  } else if (event.key === 'Home') {
    target = 0;
  } else if (event.key === 'End') {
    target = TABS.length - 1;
  }
  if (target === null) {
    return;
  }
  event.preventDefault();
  const tab = TABS[target];
  if (!tab) {
    return;
  }
  selectTab(tab.key);
  void nextTick(() => document.getElementById(`analytics-tab-id-${tab.key}`)?.focus());
}

onMounted(() => {
  loadPeriod();
  void loadEqualAge();
});
</script>

<template>
  <section class="analytics-page" data-testid="analytics-page">
    <PageHeader
      title="Analytics"
      subtitle="Kennzahlen Ihrer Beiträge nach Zeitraum, Kanal, Serie und Veröffentlichungszeit."
    />

    <div class="analytics-page__tabs" role="tablist" aria-label="Analytics-Ansichten" @keydown="onTabKeydown">
      <button
        v-for="tab in TABS"
        :id="`analytics-tab-id-${tab.key}`"
        :key="tab.key"
        type="button"
        role="tab"
        class="analytics-page__tab"
        :class="{ 'analytics-page__tab--active': active === tab.key }"
        :aria-selected="active === tab.key ? 'true' : 'false'"
        :aria-controls="active === tab.key ? `analytics-panel-${tab.key}` : undefined"
        :tabindex="active === tab.key ? 0 : -1"
        :data-testid="tab.testId"
        @click="selectTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      :id="`analytics-panel-${active}`"
      role="tabpanel"
      class="analytics-page__panel"
      data-testid="analytics-panel"
      tabindex="0"
      :aria-labelledby="`analytics-tab-id-${active}`"
    >
      <AnalyticsOverviewPanel
        v-if="active === 'overview'"
        :status="overviewStatus"
        :overview="overview"
        :days="days"
        :range-label="rangeLabel"
        error-message="Die Analytics konnten nicht geladen werden."
        @retry="loadOverview"
        @change-period="changePeriod"
      />
      <AnalyticsChannelsPanel
        v-else-if="active === 'channels'"
        :status="overviewStatus"
        :channels="overview?.channels ?? null"
        :range-label="rangeLabel"
        error-message="Die Analytics konnten nicht geladen werden."
        @retry="loadOverview"
      />
      <AnalyticsSeriesPanel
        v-else-if="active === 'series'"
        :status="seriesStatus"
        :series="series?.series ?? null"
        :range-label="rangeLabel"
        error-message="Die Serien-Analytics konnten nicht geladen werden."
        @retry="loadSeries"
      />
      <AnalyticsTimePanel
        v-else
        :status="timeStatus"
        :equal-age="equalAge"
        :choice="choice"
        error-message="Die Auswertung nach Veröffentlichungszeit konnte nicht geladen werden."
        @retry="loadEqualAge"
        @change="changeChoice"
      />
    </div>
  </section>
</template>

<style scoped>
.analytics-page {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.analytics-page__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
}

.analytics-page__tab {
  min-height: 40px;
  padding: 0 16px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--md-sys-color-on-surface-variant);
  font: inherit;
  cursor: pointer;
}

.analytics-page__tab--active {
  border-bottom-color: var(--md-sys-color-primary);
  color: var(--md-sys-color-primary);
}

.analytics-page__tab:focus-visible,
.analytics-page__panel:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}

.analytics-page__panel {
  min-width: 0;
}
</style>
