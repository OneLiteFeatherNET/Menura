<script setup lang="ts">
import { computed } from 'vue';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import LoadState from '../ui/LoadState.vue';
import StatusBadge from '../ui/StatusBadge.vue';
import {
  AUDIENCE_LABELS,
  WEEKDAY_LABELS,
  coverageLabel,
  warningText,
  type ChannelCoverage,
  type Series,
  type SeriesOverview,
} from '../../api/series';

// The series list, the audience warning and the weekly coverage. Data arrives as props; a row selection is reported as an
// event, so the page decides what opens.
const props = withDefaults(
  defineProps<{
    status: 'loading' | 'error' | 'empty' | 'ready';
    overview: SeriesOverview | null;
    errorMessage?: string;
  }>(),
  { errorMessage: 'Die Serien konnten nicht geladen werden.' },
);

const emit = defineEmits<{ retry: []; open: [id: string] }>();

const seriesColumns: DataColumn[] = [
  { key: 'name', label: 'Name' },
  { key: 'hashtag', label: 'Hashtag' },
  { key: 'audience', label: 'Zielgruppe' },
  { key: 'slot', label: 'Termin' },
  { key: 'state', label: 'Status' },
];

const weekColumns: DataColumn[] = [
  { key: 'isoWeek', label: 'Woche' },
  { key: 'coverage', label: 'Zuordnung' },
  { key: 'flag', label: 'Hinweis' },
];

function seriesRow(series: Series): Record<string, unknown> {
  return {
    id: series.id,
    name: series.name,
    hashtag: series.hashtag,
    audience: AUDIENCE_LABELS[series.audience],
    slot: `${WEEKDAY_LABELS[series.weekday - 1] ?? ''}, ${series.postingSlot} Uhr`,
    state: series.active ? 'aktiv' : 'inaktiv',
  };
}

const seriesRows = computed(() => (props.overview?.series ?? []).map(seriesRow));

function coverageRows(channel: ChannelCoverage): Record<string, unknown>[] {
  return channel.weeks.map((week) => ({
    isoWeek: week.isoWeek,
    coverage: coverageLabel(week),
    flag: week.flagged ? 'unter 90 %' : '',
  }));
}

function select(row: Record<string, unknown>) {
  emit('open', String(row.id));
}
</script>

<template>
  <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
    <section class="series-overview" aria-label="Serienübersicht">
      <div v-for="warning in overview?.warnings ?? []" :key="warning" class="series-overview__warning" role="alert">
        {{ warningText(warning) }}
      </div>

      <DataTable
        caption="Serien"
        :columns="seriesColumns"
        :rows="seriesRows"
        row-key="id"
        empty-text="Noch keine Serie angelegt."
        selectable
        @select="select"
      >
        <template #cell-state="{ row }">
          <StatusBadge :label="String(row.state)" :tone="row.state === 'aktiv' ? 'success' : 'neutral'" />
        </template>
      </DataTable>

      <section v-for="channel in overview?.coverage ?? []" :key="channel.channelId" class="series-overview__channel">
        <h3 class="series-overview__heading">Zuordnung · {{ channel.displayName }}</h3>
        <DataTable
          :caption="`Zuordnung der Shorts je Woche, ${channel.displayName}`"
          :columns="weekColumns"
          :rows="coverageRows(channel)"
          row-key="isoWeek"
          empty-text="Keine Wochen."
        />
      </section>
    </section>
  </LoadState>
</template>

<style scoped>
.series-overview {
  display: grid;
  gap: 24px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.series-overview__warning {
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.series-overview__heading {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 500;
}

.series-overview__channel {
  min-width: 0;
}
</style>
