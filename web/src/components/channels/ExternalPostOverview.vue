<script setup lang="ts">
import { computed } from 'vue';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import LoadState from '../ui/LoadState.vue';
import StatusBadge from '../ui/StatusBadge.vue';
import { stateLabel, syncLabel, type ChannelState, type PostRow } from '../../api/externalPosts';

// The overview of one channel's posts. It only shows: a viewer gets no edit control. Data arrives as props.
const props = withDefaults(
  defineProps<{
    channelName: string;
    channelState: ChannelState;
    lastSuccessfulSyncAt: string | null;
    status: 'loading' | 'error' | 'empty' | 'ready';
    rows: PostRow[];
    errorMessage?: string;
  }>(),
  { errorMessage: 'Die Beiträge konnten nicht geladen werden.' },
);

defineEmits<{ retry: [] }>();

// DataTable takes plain records; a spread gives each row the plain object type it expects.
const tableRows = computed(() => props.rows.map((row) => ({ ...row })));

const columns: DataColumn[] = [
  { key: 'title', label: 'Titel' },
  { key: 'format', label: 'Format' },
  { key: 'publishedAt', label: 'Veröffentlicht' },
  { key: 'status', label: 'Status' },
];

const tone = (state: ChannelState) =>
  state === 'connected' ? 'success' : state === 'needs_reconnection' ? 'warning' : 'neutral';

function timeOf(value: string): string {
  return new Date(value).toLocaleString('de-DE');
}
</script>

<template>
  <section class="external-posts" aria-labelledby="external-posts-title">
    <header class="external-posts__header">
      <h2 id="external-posts-title" class="external-posts__title">Beiträge · {{ channelName }}</h2>
      <StatusBadge :label="stateLabel(channelState)" :tone="tone(channelState)" />
    </header>
    <p class="external-posts__sync">{{ syncLabel(lastSuccessfulSyncAt) }}</p>
    <p v-if="channelState === 'needs_reconnection'" class="external-posts__waiting">
      Der Import wartet auf die Neuverbindung des Kanals. Die bisherigen Beiträge bleiben erhalten.
    </p>
    <LoadState :status="status" :error-message="errorMessage" empty-text="Keine Beiträge." @retry="$emit('retry')">
      <DataTable caption="Beiträge des Kanals" :columns="columns" :rows="tableRows" row-key="id"
        empty-text="Keine Beiträge.">
        <template #cell-title="{ row }">
          <a v-if="row.link" :href="String(row.link)" target="_blank" rel="noopener noreferrer">{{ row.title }}</a>
          <template v-else>{{ row.title }}</template>
        </template>
        <template #cell-publishedAt="{ row }">{{ timeOf(String(row.publishedAt)) }}</template>
        <template #cell-status="{ row }">
          <StatusBadge :label="String(row.status)" :tone="row.status === 'Entfernt' ? 'warning' : 'success'" />
        </template>
      </DataTable>
    </LoadState>
  </section>
</template>

<style scoped>
.external-posts {
  display: grid;
  gap: 8px;
  font-family: Roboto, system-ui, sans-serif;
}

.external-posts__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.external-posts__title {
  margin: 0;
  font-size: 18px;
  font-weight: 400;
  line-height: 24px;
}

.external-posts__sync,
.external-posts__waiting {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
}

.external-posts__waiting {
  color: var(--md-sys-color-on-surface-variant);
}
</style>
