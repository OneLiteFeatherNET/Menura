<script setup lang="ts">
import { computed } from 'vue';
import type { AnalyticsOverview, ChannelCollection } from '../../api/analytics';
import {
  METRIC_ORDER,
  NO_POSTS_TEXT,
  NO_POSTS_TITLE,
  PERIOD_OPTIONS,
  formatMetricValue,
  metricLabel,
  metricOf,
  outcomeLabel,
  timestampLabel,
} from '../../analytics/format';
import AppSelect from '../ui/AppSelect.vue';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import EmptyState from '../ui/EmptyState.vue';
import LoadState from '../ui/LoadState.vue';

// The totals of the period, the collection status of each channel and the period selector. The data arrives as props;
// a new period is reported as an event, and the page loads it.
const props = defineProps<{
  status: 'loading' | 'error' | 'ready';
  overview: AnalyticsOverview | null;
  days: number;
  rangeLabel: string;
  errorMessage?: string;
}>();

const emit = defineEmits<{ retry: []; 'change-period': [days: number] }>();

const totalColumns: DataColumn[] = [
  { key: 'metric', label: 'Kennzahl' },
  { key: 'value', label: 'Wert' },
  { key: 'posts', label: 'Beiträge mit Wert' },
];

const collectionColumns: DataColumn[] = [
  { key: 'channel', label: 'Kanal' },
  { key: 'platform', label: 'Plattform' },
  { key: 'outcome', label: 'Status' },
  { key: 'lastSuccess', label: 'Letzte Erfassung' },
  { key: 'failure', label: 'Fehler' },
];

const totalRows = computed(() =>
  METRIC_ORDER.map((name) => {
    const metric = metricOf(props.overview?.totals.metrics ?? [], name);
    return {
      metric: metricLabel(name),
      value: formatMetricValue(metric),
      posts: String(metric?.posts ?? 0),
    };
  }),
);

function collectionRow(collection: ChannelCollection): Record<string, unknown> {
  return {
    id: collection.channelId,
    channel: collection.displayName,
    platform: collection.platform,
    outcome: outcomeLabel(collection.outcome),
    lastSuccess: timestampLabel(collection.lastSuccessAt),
    failure: collection.failureReason ?? '',
  };
}

const collectionRows = computed(() => (props.overview?.collections ?? []).map(collectionRow));

function onPeriod(value: string) {
  emit('change-period', Number(value));
}
</script>

<template>
  <section class="analytics-panel">
    <h2 class="analytics-panel__heading">Überblick</h2>
    <div class="analytics-panel__controls">
      <AppSelect label="Zeitraum" :options="PERIOD_OPTIONS" :model-value="String(days)" @update:model-value="onPeriod" />
      <p class="analytics-panel__range">Zeitraum: {{ rangeLabel }}</p>
    </div>

    <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
      <template v-if="overview">
        <EmptyState v-if="overview.totals.posts === 0" :title="NO_POSTS_TITLE" :description="NO_POSTS_TEXT" />
        <DataTable
          v-else
          caption="Gesamtwerte des Zeitraums"
          :columns="totalColumns"
          :rows="totalRows"
          row-key="metric"
        />

        <DataTable
          caption="Erfassung je Kanal"
          :columns="collectionColumns"
          :rows="collectionRows"
          row-key="id"
          empty-text="Noch kein Kanal verbunden."
        />
      </template>
    </LoadState>
  </section>
</template>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.analytics-panel__heading {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  line-height: 24px;
}

.analytics-panel__controls {
  display: grid;
  gap: 4px;
  max-width: 320px;
  min-width: 0;
}

.analytics-panel__range {
  margin: 0;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}
</style>
