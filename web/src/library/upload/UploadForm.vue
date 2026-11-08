<script setup lang="ts">
// The declaration of a file before its upload starts: what the file is, and for raw material its stream title and
// date. Duration and resolution come from the file where the browser can read them and stay editable.
import { useId } from 'vue';
import AppSelect from '../../components/ui/AppSelect.vue';
import AppTextField from '../../components/ui/AppTextField.vue';
import type { DeclarationField, UploadMode } from './declaration';

export interface UploadFormValues {
  durationSeconds: string;
  width: string;
  height: string;
  streamTitle: string;
  streamDate: string;
}

const props = defineProps<{
  mode: UploadMode;
  fileName: string;
  values: UploadFormValues;
  errors: Partial<Record<DeclarationField, string>>;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  'update:mode': [mode: UploadMode];
  'update:values': [values: UploadFormValues];
  pick: [file: File];
}>();

const fileInputId = `${useId()}-file`;

const MODE_OPTIONS = [
  { value: 'finished', label: 'Fertiges Video oder Bild' },
  { value: 'raw', label: 'Rohmaterial (Stream-Aufnahme)' },
];

function set<K extends keyof UploadFormValues>(key: K, value: string) {
  emit('update:values', { ...props.values, [key]: value });
}

function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (file) emit('pick', file);
}
</script>

<template>
  <form class="upload-form" @submit.prevent>
    <div class="upload-form__file">
      <label :for="fileInputId" class="upload-form__label">Datei</label>
      <input
        :id="fileInputId"
        class="upload-form__input"
        type="file"
        accept=".mp4,.mov,.webm,.mkv,.png,.jpg,.jpeg,.webp,.gif,video/*,image/*"
        :disabled="disabled"
        @change="onFile"
      />
      <p v-if="fileName" class="upload-form__hint">Gewählt: {{ fileName }}</p>
    </div>

    <AppSelect
      label="Art des Materials"
      :model-value="mode"
      :options="MODE_OPTIONS"
      :disabled="disabled"
      @update:model-value="emit('update:mode', $event as UploadMode)"
    />

    <AppTextField
      label="Länge in Sekunden"
      :model-value="values.durationSeconds"
      :disabled="disabled"
      :error="errors.durationSeconds"
      @update:model-value="set('durationSeconds', $event)"
    />

    <div class="upload-form__row">
      <AppTextField
        label="Breite in Pixeln"
        :model-value="values.width"
        :disabled="disabled"
        :error="errors.width"
        @update:model-value="set('width', $event)"
      />
      <AppTextField
        label="Höhe in Pixeln"
        :model-value="values.height"
        :disabled="disabled"
        :error="errors.height"
        @update:model-value="set('height', $event)"
      />
    </div>

    <p v-if="errors.format" class="upload-form__error" role="alert">{{ errors.format }}</p>

    <template v-if="mode === 'raw'">
      <AppTextField
        label="Titel des Streams"
        :model-value="values.streamTitle"
        :disabled="disabled"
        :error="errors.streamTitle"
        :maxlength="200"
        @update:model-value="set('streamTitle', $event)"
      />
      <AppTextField
        label="Datum des Streams (JJJJ-MM-TT)"
        placeholder="2026-11-08"
        :model-value="values.streamDate"
        :disabled="disabled"
        :error="errors.streamDate"
        @update:model-value="set('streamDate', $event)"
      />
    </template>
  </form>
</template>

<style scoped>
.upload-form {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.upload-form__file {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.upload-form__label {
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-on-surface-variant);
}

.upload-form__input {
  font: inherit;
  font-size: 14px;
  max-width: 100%;
}

.upload-form__hint {
  margin: 0;
  font-size: 13px;
  overflow-wrap: anywhere;
  color: var(--md-sys-color-on-surface-variant);
}

.upload-form__row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.upload-form__error {
  margin: 0;
  font-size: 13px;
  color: var(--md-sys-color-error);
}
</style>
