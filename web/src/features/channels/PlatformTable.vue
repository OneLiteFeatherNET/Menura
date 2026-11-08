<script setup lang="ts">
import type { Platform } from '../../api/channels';
import DataTable from '../../components/ui/DataTable.vue';
import StatusBadge from '../../components/ui/StatusBadge.vue';
import type { PlatformRow, Tone } from './model';

// The platforms of this build, with what they support and the audit status of their app. Only an administrator sees
// the action that records the audit status, because that status decides whether public visibility may be used.
const props = defineProps<{
  rows: PlatformRow[];
  canAdminister: boolean;
}>();

const emit = defineEmits<{ openAudit: [platform: Platform] }>();

const columns = [
  { key: 'name', label: 'Plattform' },
  { key: 'capabilities', label: 'Funktionen' },
  { key: 'features', label: 'Kanalfunktionen' },
  { key: 'auditLabel', label: 'Prüfstatus' },
  { key: 'restrictions', label: 'Einschränkungen' },
  ...(props.canAdminister ? [{ key: 'actions', label: 'Aktionen' }] : []),
];
</script>

<template>
  <DataTable caption="Plattformen" :columns="columns" :rows="rows" row-key="id" empty-text="Keine Plattform in diesem Build.">
    <template #cell-auditLabel="{ row }">
      <StatusBadge :label="String(row.auditLabel)" :tone="row.auditTone as Tone" />
    </template>
    <template v-if="canAdminister" #cell-actions="{ row }">
      <button type="button" class="platform-table__button" @click="emit('openAudit', row.platform as Platform)">
        Prüfstatus erfassen
      </button>
    </template>
  </DataTable>
</template>

<style scoped>
.platform-table__button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.platform-table__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
