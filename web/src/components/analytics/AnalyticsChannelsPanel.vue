<script setup lang="ts">
import { computed } from 'vue';
import type { Aggregate } from '../../api/analytics';
import {
  METRIC_ORDER,
  NO_POSTS_TEXT,
  NO_POSTS_TITLE,
  formatMetricValue,
  metricLabel,
  metricOf,
} from '../../analytics/format';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import EmptyState from '../ui/EmptyState.vue';
import LoadState from '../ui/LoadState.vue';
import BarChart from './BarChart.vue';

// The aggregate of each channel as a table and the views as bars. The channels arrive as props from the overview.
const props = defineProps<{
  status: 'loading' | 'error' | 'ready';
  channels: Aggregate[] | null;
  rangeLabel: string;
  errorMessage?: string;
}>();

const emit = defineEmits<{ retry: [] }>();

const columns = computed<DataColumn[]>(() => [
  { key: 'channel', label: 'Kanal' },
  { key: 'posts', label: 'Beiträge' },
  ...METRIC_ORDER.map((name) => ({ key: name, label: metricLabel(name) })),
]);

function channelName(channel: Aggregate): string {
  return channel.label ?? channel.key;
}

const rows = computed(() =>
  (props.channels ?? []).map((channel) => {
    const row: Record<string, unknown> = { id: channel.key, channel: channelName(channel), posts: String(channel.posts) };
    for (const name of METRIC_ORDER) {
      row[name] = formatMetricValue(metricOf(channel.metrics, name));
    }
    return row;
  }),
);

const viewBars = computed(() =>
  (props.channels ?? []).map((channel) => ({
    label: channelName(channel),
    value: metricOf(channel.metrics, 'views')?.value ?? null,
  })),
);

const hasPosts = computed(() => (props.channels ?? []).some((channel) => channel.posts > 0));
</script>

<template>
  <section class="analytics-panel">
    <h2 class="analytics-panel__heading">Kanäle</h2>
    <p class="analytics-panel__range">Zeitraum: {{ rangeLabel }}</p>

    <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
      <EmptyState v-if="!hasPosts" :title="NO_POSTS_TITLE" :description="NO_POSTS_TEXT" />
      <template v-else>
        <DataTable caption="Kennzahlen je Kanal" :columns="columns" :rows="rows" row-key="id" />
        <BarChart caption="Aufrufe je Kanal" :items="viewBars" />
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

.analytics-panel__range {
  margin: 0;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}
</style>
