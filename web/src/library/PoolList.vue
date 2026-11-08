<script setup lang="ts">
// The pool as a table. Rows are the items of the server; the list only shows them and reports the request for more.
import DataTable from '../components/ui/DataTable.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import TagChips from '../components/ui/TagChips.vue';
import LoadState from '../components/ui/LoadState.vue';
import type { PoolItem } from './pool';

const props = defineProps<{
  items: PoolItem[];
  status: 'loading' | 'error' | 'ready';
  hasMore: boolean;
  errorMessage?: string;
  canEdit?: boolean;
}>();
const emit = defineEmits<{ more: []; retry: [], transcript: [itemId: string] }>();

const columns: { key: string; label: string }[] = [
  { key: 'title', label: 'Titel' },
  { key: 'kind', label: 'Art' },
  { key: 'stage', label: 'Stufe' },
  { key: 'status', label: 'Status' },
  { key: 'usage', label: 'Verwendung' },
  { key: 'uploader', label: 'Hochgeladen von' },
  { key: 'tags', label: 'Tags' },
  { key: 'actions', label: 'Aktion' },
];

const KIND_LABELS: Record<string, string> = { raw: 'Rohmaterial', clip: 'Clip' };
const STAGE_LABELS: Record<string, string> = { raw: 'Roh', cut: 'Geschnitten', rendered: 'Gerendert' };
const USAGE_LABELS: Record<string, string> = { used: 'Verwendet', never_used: 'Nie verwendet' };

// Rows are plain records for the table; the detail of each item stays in the item itself.
const rows = () =>
  props.items.map((item) => ({
    id: item.id,
    title: item.title ?? 'Ohne Titel',
    kind: item.kind,
    stage: item.stage,
    status: item.status,
    usage: item.usage,
    uploader: item.uploaderId.slice(0, 8),
    tags: [...item.personTags.map((person) => `@${person.slice(0, 8)}`), ...item.freeTags],
  }));

function stageTone(stage: string): 'neutral' | 'info' | 'success' {
  return stage === 'rendered' ? 'success' : stage === 'cut' ? 'info' : 'neutral';
}
</script>

<template>
  <section class="pool-list" aria-label="Pool-Einträge">
    <LoadState :status="status" :error-message="errorMessage ?? 'Der Pool konnte nicht geladen werden.'" @retry="emit('retry')">
      <DataTable caption="Content-Pool" :columns="columns" :rows="rows()" row-key="id" empty-text="Keine Einträge gefunden.">
        <template #cell-kind="{ row }">
          <StatusBadge :label="KIND_LABELS[row.kind as string] ?? String(row.kind)" />
        </template>
        <template #cell-stage="{ row }">
          <StatusBadge :label="STAGE_LABELS[row.stage as string] ?? String(row.stage)" :tone="stageTone(String(row.stage))" />
        </template>
        <template #cell-status="{ row }">
          <StatusBadge v-if="row.status === 'suggested'" label="Vorschlag" tone="warning" />
          <span v-else>Aktiv</span>
        </template>
        <template #cell-usage="{ row }">
          <span v-if="row.usage">{{ USAGE_LABELS[row.usage as string] }}</span>
        </template>
        <template #cell-tags="{ row }">
          <TagChips :tags="(row.tags as string[])" empty-text="Keine Tags" />
        </template>
        <template #cell-actions="{ row }">
          <button
            v-if="canEdit && row.kind === 'raw'"
            type="button"
            class="pool-list__action"
            @click="emit('transcript', String(row.id))"
          >
            Transkript
          </button>
        </template>
      </DataTable>
      <div v-if="hasMore" class="pool-list__more">
        <button type="button" class="pool-list__button" @click="emit('more')">Mehr laden</button>
      </div>
    </LoadState>
  </section>
</template>

<style scoped>
.pool-list {
  display: grid;
  gap: 12px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
}

.pool-list__more {
  display: flex;
  justify-content: center;
}

.pool-list__action {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.pool-list__button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}
</style>
