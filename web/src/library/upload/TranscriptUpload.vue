<script setup lang="ts">
// Attaches a WebVTT or SRT transcript to a raw item. The file is chosen here and sent by the dialog; a refused file
// shows the line the server named, so the editor can fix it in the editing tool and send it again.
import { ref, useId } from 'vue';

export type TranscriptPhase = 'idle' | 'uploading' | 'done';

defineProps<{
  itemTitle: string;
  phase: TranscriptPhase;
  error?: string | null;
  segmentCount?: number | null;
}>();

const emit = defineEmits<{ upload: [file: File] }>();

const inputId = `${useId()}-transcript`;
const chosen = ref<File | null>(null);

function onFile(event: Event) {
  chosen.value = (event.target as HTMLInputElement).files?.[0] ?? null;
}

function submit() {
  if (chosen.value) emit('upload', chosen.value);
}
</script>

<template>
  <section class="transcript-upload" :aria-label="`Transkript für ${itemTitle}`">
    <p class="transcript-upload__item">{{ itemTitle }}</p>
    <p class="transcript-upload__hint">
      WebVTT (.vtt) oder SRT (.srt), höchstens 10 MB. Ein neues Transkript ersetzt das bisherige vollständig.
    </p>

    <div class="transcript-upload__file">
      <label :for="inputId" class="transcript-upload__label">Transkript-Datei</label>
      <input :id="inputId" type="file" accept=".vtt,.srt,text/vtt,application/x-subrip" :disabled="phase === 'uploading'" @change="onFile" />
    </div>

    <p v-if="error" class="transcript-upload__error" role="alert">{{ error }}</p>
    <p v-if="phase === 'done'" class="transcript-upload__done">
      Transkript gespeichert{{ segmentCount !== null && segmentCount !== undefined ? `: ${segmentCount} Abschnitte` : '' }}.
    </p>

    <div class="transcript-upload__actions">
      <button
        type="button"
        class="transcript-upload__button"
        :disabled="!chosen || phase === 'uploading'"
        @click="submit"
      >
        {{ phase === 'uploading' ? 'Wird hochgeladen …' : 'Transkript hochladen' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.transcript-upload {
  display: grid;
  gap: 12px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.transcript-upload__item {
  margin: 0;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.transcript-upload__hint,
.transcript-upload__done {
  margin: 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--md-sys-color-on-surface-variant);
}

.transcript-upload__done {
  color: var(--md-sys-color-primary);
}

.transcript-upload__file {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.transcript-upload__label {
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-on-surface-variant);
}

.transcript-upload__error {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
  font-size: 14px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.transcript-upload__actions {
  display: flex;
  justify-content: flex-end;
}

.transcript-upload__button {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.transcript-upload__button:disabled {
  opacity: 0.38;
  cursor: default;
}

.transcript-upload__button:focus-visible,
.transcript-upload input:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
