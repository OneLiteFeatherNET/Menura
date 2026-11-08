<script setup lang="ts">
import { ref } from 'vue';
import type { Platform } from '../../api/channels';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppSelect from '../../components/ui/AppSelect.vue';

// Choosing a platform is the only input: the consent itself happens on the platform's own screen. No field for a
// token, key or password exists here.
const props = defineProps<{
  open: boolean;
  platforms: Platform[];
  busy: boolean;
}>();

const emit = defineEmits<{
  close: [];
  connect: [platform: string];
}>();

const selected = ref<string | null>(null);
const options = () => props.platforms.map((platform) => ({ value: platform.platform, label: platform.displayName }));

function submit() {
  if (selected.value) {
    emit('connect', selected.value);
  }
}
</script>

<template>
  <AppDialog :open="open" title="Kanal verbinden" @close="emit('close')">
    <p class="connect-dialog__text">
      Sie werden zur Einwilligung der gewählten Plattform weitergeleitet. Zugangsdaten geben Sie in Menura nicht ein.
    </p>
    <AppSelect
      label="Plattform"
      :options="options()"
      :model-value="selected"
      @update:model-value="(value: string) => (selected = value)"
    />
    <template #actions>
      <div class="connect-dialog__actions">
        <button type="button" class="connect-dialog__button" :disabled="!selected || busy" @click="submit">
          Weiter zur Plattform
        </button>
        <button type="button" class="connect-dialog__button connect-dialog__button--quiet" :disabled="busy" @click="emit('close')">
          Abbrechen
        </button>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.connect-dialog__text {
  margin: 0;
  font-family: Roboto, system-ui, sans-serif;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.connect-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.connect-dialog__button {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.connect-dialog__button--quiet {
  border: 1px solid var(--md-sys-color-outline);
  background: transparent;
  color: var(--md-sys-color-primary);
}

.connect-dialog__button:disabled {
  opacity: 0.38;
  cursor: default;
}

.connect-dialog__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
