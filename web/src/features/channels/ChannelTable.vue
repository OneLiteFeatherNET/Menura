<script setup lang="ts">
import { ref } from 'vue';
import type { Channel } from '../../api/channels';
import DataTable from '../../components/ui/DataTable.vue';
import ConfirmInline from '../../components/ui/ConfirmInline.vue';
import StatusBadge from '../../components/ui/StatusBadge.vue';
import { canDisconnect, canReconnect, type ChannelRow, type Tone } from './model';

// The channels of the hub with their connection state. A viewer sees the state and the reason only; the actions are
// shown to publishers and administrators, the roles the server accepts for them. Disconnect asks first, in place.
const props = defineProps<{
  rows: ChannelRow[];
  canManage: boolean;
  busy: boolean;
}>();

const emit = defineEmits<{
  reconnect: [channel: Channel];
  disconnect: [channel: Channel];
}>();

const confirming = ref<string | null>(null);

const columns = [
  { key: 'name', label: 'Kanal' },
  { key: 'platform', label: 'Plattform' },
  { key: 'state', label: 'Verbindung' },
  { key: 'reason', label: 'Grund' },
  { key: 'scopes', label: 'Freigaben' },
  { key: 'quota', label: 'Kontingent' },
  { key: 'audit', label: 'Prüfstatus' },
  ...(props.canManage ? [{ key: 'actions', label: 'Aktionen' }] : []),
];

function confirmMessage(row: Record<string, unknown>): string {
  return `Den Kanal „${String(row.name)}“ trennen? Die gespeicherten Zugangsdaten werden gelöscht.`;
}

function startDisconnect(row: Record<string, unknown>) {
  confirming.value = String(row.id);
}

function confirmDisconnect(row: Record<string, unknown>) {
  confirming.value = null;
  emit('disconnect', row.channel as Channel);
}
</script>

<template>
  <DataTable caption="Verbundene Kanäle" :columns="columns" :rows="rows" row-key="id" empty-text="Noch kein Kanal verbunden.">
    <template #cell-state="{ row }">
      <StatusBadge :label="String(row.stateLabel)" :tone="row.stateTone as Tone" />
    </template>
    <template v-if="canManage" #cell-actions="{ row }">
      <div class="channel-table__actions">
        <template v-if="canReconnect(row.channel as Channel)">
          <button
            type="button"
            class="channel-table__button channel-table__button--primary"
            :disabled="busy"
            @click="emit('reconnect', row.channel as Channel)"
          >
            Neu verbinden
          </button>
        </template>
        <template v-if="canDisconnect(row.channel as Channel)">
          <button
            v-if="confirming !== row.id"
            type="button"
            class="channel-table__button"
            :disabled="busy"
            @click="startDisconnect(row)"
          >
            Trennen
          </button>
          <ConfirmInline
            v-else
            :message="confirmMessage(row)"
            confirm-label="Kanal trennen"
            cancel-label="Abbrechen"
            tone="danger"
            :busy="busy"
            @confirm="confirmDisconnect(row)"
            @cancel="confirming = null"
          />
        </template>
      </div>
    </template>
  </DataTable>
</template>

<style scoped>
.channel-table__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.channel-table__button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.channel-table__button--primary {
  border: 0;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
}

.channel-table__button:disabled {
  opacity: 0.38;
  cursor: default;
}

.channel-table__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
