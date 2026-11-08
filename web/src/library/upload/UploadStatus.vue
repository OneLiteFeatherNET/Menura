<script setup lang="ts">
// What one upload shows in each phase. The component only renders the state it is given and reports the editor's
// choices as events; the sequence itself runs in the dialog.
import { computed } from 'vue';
import ProgressBar from '../../components/ui/ProgressBar.vue';
import { percentOf, type UploadState } from './uploadState';

const props = defineProps<{
  state: UploadState;
}>();

const emit = defineEmits<{
  retry: [];
  cancel: [];
  confirmDuplicate: [];
  close: [];
}>();

const percent = computed(() => percentOf(props.state));
const canCancel = computed(() => props.state.sessionId !== null && ['uploading', 'flagged', 'failed'].includes(props.state.phase));
</script>

<template>
  <section class="upload-status" aria-live="polite" :aria-label="state.fileName || 'Upload'">
    <p v-if="state.fileName" class="upload-status__file">{{ state.fileName }}</p>

    <template v-if="state.phase === 'hashing'">
      <p class="upload-status__text">Prüfsumme wird berechnet: {{ state.hashedParts }} von {{ state.partCount }} Teilen.</p>
      <ProgressBar :value="percent" label="Prüfsumme" />
    </template>

    <template v-else-if="state.phase === 'uploading'">
      <p class="upload-status__text">
        Hochladen: {{ state.confirmedParts.length }} von {{ state.partCount }} Teilen im Speicher.
      </p>
      <ProgressBar :value="percent" label="Hochladen" />
    </template>

    <template v-else-if="state.phase === 'flagged'">
      <p class="upload-status__text" role="alert">
        Diese Datei wurde schon hochgeladen. Der bestehende Eintrag bleibt erhalten. Soll die Datei trotzdem als zweiter
        Eintrag hochgeladen werden?
      </p>
    </template>

    <template v-else-if="state.phase === 'completing'">
      <p class="upload-status__text">Der Speicher bestätigt die Datei. Einen Moment bitte.</p>
    </template>

    <template v-else-if="state.phase === 'completed'">
      <p class="upload-status__text upload-status__text--success">
        Der Upload ist abgeschlossen. Die Datei steht jetzt im Pool.
      </p>
    </template>

    <template v-else-if="state.phase === 'failed'">
      <p class="upload-status__text" role="alert">
        {{ state.message ?? 'Der Upload ist fehlgeschlagen.' }}
      </p>
    </template>

    <p v-else-if="state.phase === 'cancelled'" class="upload-status__text">Der Upload wurde abgebrochen.</p>

    <div class="upload-status__actions">
      <button v-if="state.phase === 'flagged'" type="button" class="upload-status__button upload-status__button--primary" @click="emit('confirmDuplicate')">
        Trotzdem hochladen
      </button>
      <button v-if="state.phase === 'failed' && state.sessionId" type="button" class="upload-status__button upload-status__button--primary" @click="emit('retry')">
        Erneut versuchen
      </button>
      <button v-if="canCancel" type="button" class="upload-status__button" @click="emit('cancel')">Abbrechen</button>
      <button v-if="state.phase === 'completed' || state.phase === 'cancelled'" type="button" class="upload-status__button upload-status__button--primary" @click="emit('close')">
        Fertig
      </button>
    </div>
  </section>
</template>

<style scoped>
.upload-status {
  display: grid;
  gap: 12px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.upload-status__file {
  margin: 0;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.upload-status__text {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.upload-status__text--success {
  color: var(--md-sys-color-primary);
}

.upload-status__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.upload-status__button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.upload-status__button--primary {
  border: 0;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
}

.upload-status__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
