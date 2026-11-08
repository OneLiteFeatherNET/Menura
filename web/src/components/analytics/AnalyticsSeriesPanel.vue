<script setup lang="ts">
import { computed } from 'vue';
import type { SeriesAnalytics } from '../../api/analytics';
import {
  NO_POSTS_TEXT,
  NO_POSTS_TITLE,
  formatMetricValue,
  metricOf,
  rankSeries,
} from '../../analytics/format';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import EmptyState from '../ui/EmptyState.vue';
import LoadState from '../ui/LoadState.vue';

// The series ranked by engaged views, highest first, with the average view percentage. A series whose platform gives no
// value reads 'nicht verfügbar'. The posts in no series are listed last under their own name.
const props = defineProps<{
  status: 'loading' | 'error' | 'ready';
  series: SeriesAnalytics[] | null;
  rangeLabel: string;
  errorMessage?: string;
}>();

const emit = defineEmits<{ retry: [] }>();

const columns: DataColumn[] = [
  { key: 'rank', label: 'Rang' },
  { key: 'series', label: 'Serie' },
  { key: 'posts', label: 'Beiträge' },
  { key: 'engaged', label: 'Engagierte Aufrufe' },
  { key: 'average', label: 'Ø Anteil angesehen' },
];

const ranked = computed(() => rankSeries(props.series ?? []));

const rows = computed(() =>
  ranked.value.map((series, index) => ({
    id: series.seriesId ?? 'none',
    rank: String(index + 1),
    series: series.label ?? 'Ohne Serie',
    posts: String(series.posts),
    engaged: formatMetricValue(metricOf(series.metrics, 'engaged_views')),
    average: formatMetricValue(metricOf(series.metrics, 'average_view_percentage')),
  })),
);

const hasPosts = computed(() => ranked.value.some((series) => series.posts > 0));
</script>

<template>
  <section class="analytics-panel">
    <h2 class="analytics-panel__heading">Serien</h2>
    <p class="analytics-panel__range">Zeitraum: {{ rangeLabel }}</p>

    <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
      <EmptyState v-if="!hasPosts" :title="NO_POSTS_TITLE" :description="NO_POSTS_TEXT" />
      <DataTable v-else caption="Serien nach engagierten Aufrufen" :columns="columns" :rows="rows" row-key="id" />
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
