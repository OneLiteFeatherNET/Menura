<script setup lang="ts">
import { ref, watch } from 'vue';
import type { AuditStatusValue, Platform } from '../../api/channels';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppSelect from '../../components/ui/AppSelect.vue';
import AppTextField from '../../components/ui/AppTextField.vue';
import { auditLabel } from './model';

// Records the audit status of a platform app with the source of the decision. The form starts from the recorded
// status, so an administrator sees what is saved before changing it.
const props = defineProps<{
  open: boolean;
  platform: Platform | null;
  busy: boolean;
}>();

const emit = defineEmits<{
  close: [];
  save: [request: { platform: string; status: AuditStatusValue; source: string }];
}>();

const status = ref<AuditStatusValue | null>(null);
const source = ref('');

watch(
  () => props.platform,
  (platform) => {
    status.value = platform?.audit.status ?? null;
    source.value = platform?.audit.source ?? '';
  },
  { immediate: true },
);

const statusOptions = (['unknown', 'unaudited', 'audited'] as AuditStatusValue[]).map((value) => ({
  value,
  label: auditLabel(value),
}));

function save() {
  if (props.platform && status.value) {
    emit('save', { platform: props.platform.platform, status: status.value, source: source.value.trim() });
  }
}
</script>

<template>
  <AppDialog :open="open" :title="`Prüfstatus: ${platform?.displayName ?? ''}`" @close="emit('close')">
    <p class="audit-dialog__text">
      Ein unbekannter oder nicht geprüfter Status gilt wie „nicht geprüft“: Öffentliche Veröffentlichungen sind dann nicht
      verfügbar. Nennen Sie die Quelle der Entscheidung, etwa das Prüfschreiben.
    </p>
    <AppSelect
      label="Status"
      :options="statusOptions"
      :model-value="status"
      @update:model-value="(value: string) => (status = value as AuditStatusValue)"
    />
    <AppTextField
      label="Quelle der Entscheidung"
      :model-value="source"
      :maxlength="200"
      placeholder="z. B. Prüfschreiben vom Datum"
      @update:model-value="(value: string) => (source = value)"
    />
    <template #actions>
      <div class="audit-dialog__actions">
        <button type="button" class="audit-dialog__button" :disabled="!status || busy" @click="save">Speichern</button>
        <button type="button" class="audit-dialog__button audit-dialog__button--quiet" :disabled="busy" @click="emit('close')">
          Abbrechen
        </button>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.audit-dialog__text {
  margin: 0;
  font-family: Roboto, system-ui, sans-serif;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.audit-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.audit-dialog__button {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.audit-dialog__button--quiet {
  border: 1px solid var(--md-sys-color-outline);
  background: transparent;
  color: var(--md-sys-color-primary);
}

.audit-dialog__button:disabled {
  opacity: 0.38;
  cursor: default;
}

.audit-dialog__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
