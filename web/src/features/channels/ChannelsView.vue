<script setup lang="ts">
import type { AuditStatusValue, Channel, Platform } from '../../api/channels';
import LoadState from '../../components/ui/LoadState.vue';
import PageHeader from '../../components/ui/PageHeader.vue';
import AuditStatusDialog from './AuditStatusDialog.vue';
import ChannelTable from './ChannelTable.vue';
import ConnectChannelDialog from './ConnectChannelDialog.vue';
import PlatformTable from './PlatformTable.vue';
import { channelRows, platformRows, type ConnectionPermissions } from './model';

// The channels page without data access: it shows what the container passes and reports every action as an event.
defineProps<{
  status: 'loading' | 'error' | 'ready';
  platforms: Platform[];
  channels: Channel[];
  permissions: ConnectionPermissions;
  busy: boolean;
  notice: string | null;
  actionError: string | null;
  connectOpen: boolean;
  auditTarget: Platform | null;
}>();

const emit = defineEmits<{
  retry: [];
  openConnect: [];
  closeConnect: [];
  connect: [platform: string];
  reconnect: [channel: Channel];
  disconnect: [channel: Channel];
  openAudit: [platform: Platform];
  closeAudit: [];
  saveAudit: [request: { platform: string; status: AuditStatusValue; source: string }];
}>();
</script>

<template>
  <div class="channels-view">
    <PageHeader
      title="Kanäle"
      subtitle="Verbundene Kanäle der Plattformen. Zugangsdaten geben Sie nie ein: Die Verbindung entsteht über die Einwilligung der Plattform."
    >
      <button
        v-if="permissions.canConnect && status === 'ready'"
        type="button"
        class="channels-view__primary"
        :disabled="busy || platforms.length === 0"
        @click="emit('openConnect')"
      >
        Kanal verbinden
      </button>
    </PageHeader>

    <p v-if="notice" role="status" class="channels-view__notice">{{ notice }}</p>
    <p v-if="actionError" role="alert" class="channels-view__error">{{ actionError }}</p>

    <LoadState
      :status="status"
      loadingLabel="Kanäle werden geladen …"
      errorMessage="Kanäle und Plattformen konnten nicht geladen werden."
      @retry="emit('retry')"
    >
      <section class="channels-view__section" aria-labelledby="channels-view-connected">
        <h2 id="channels-view-connected" class="channels-view__heading">Verbundene Kanäle</h2>
        <ChannelTable
          :rows="channelRows(channels)"
          :can-manage="permissions.canConnect"
          :busy="busy"
          @reconnect="(channel) => emit('reconnect', channel)"
          @disconnect="(channel) => emit('disconnect', channel)"
        />
      </section>
      <section class="channels-view__section" aria-labelledby="channels-view-platforms">
        <h2 id="channels-view-platforms" class="channels-view__heading">Plattformen</h2>
        <PlatformTable
          :rows="platformRows(platforms)"
          :can-administer="permissions.canAdminister"
          @open-audit="(platform) => emit('openAudit', platform)"
        />
      </section>
    </LoadState>

    <ConnectChannelDialog
      :open="connectOpen"
      :platforms="platforms"
      :busy="busy"
      @close="emit('closeConnect')"
      @connect="(platform) => emit('connect', platform)"
    />
    <AuditStatusDialog
      :open="auditTarget !== null"
      :platform="auditTarget"
      :busy="busy"
      @close="emit('closeAudit')"
      @save="(request) => emit('saveAudit', request)"
    />
  </div>
</template>

<style scoped>
.channels-view {
  display: grid;
  gap: 24px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.channels-view__section {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.channels-view__heading {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.channels-view__notice,
.channels-view__error {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  overflow-wrap: anywhere;
  font-size: 14px;
}

.channels-view__notice {
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
}

.channels-view__error {
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
}

.channels-view__primary {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.channels-view__primary:disabled {
  opacity: 0.38;
  cursor: default;
}

.channels-view__primary:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
