<script setup lang="ts">
import { computed, useId } from 'vue';

const props = withDefaults(
  defineProps<{
    label: string;
    modelValue: string;
    placeholder?: string;
    disabled?: boolean;
    error?: string;
    maxlength?: number;
    type?: 'text' | 'search' | 'url';
  }>(),
  { placeholder: undefined, disabled: false, error: undefined, maxlength: undefined, type: 'text' },
);

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const uid = useId();
const inputId = `${uid}-input`;
const errorId = `${uid}-error`;
const counterId = `${uid}-count`;

const counter = computed(() => (props.maxlength ? `${props.modelValue.length}/${props.maxlength}` : ''));
</script>

<template>
  <div class="app-text-field" :class="{ 'app-text-field--error': !!error, 'app-text-field--disabled': disabled }">
    <label class="app-text-field__label" :for="inputId">{{ label }}</label>
    <input
      :id="inputId"
      class="app-text-field__input"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :maxlength="maxlength"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="[error ? errorId : '', counter ? counterId : ''].filter(Boolean).join(' ') || undefined"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <div class="app-text-field__footer">
      <p v-if="error" :id="errorId" class="app-text-field__error">{{ error }}</p>
      <span v-if="counter" :id="counterId" class="app-text-field__counter">{{ counter }}</span>
    </div>
  </div>
</template>

<style scoped>
.app-text-field {
  display: grid;
  gap: 4px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.app-text-field__label {
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.app-text-field__input {
  box-sizing: border-box;
  width: 100%;
  min-height: 48px;
  padding: 0 12px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 4px;
  background: var(--md-sys-color-surface);
  color: inherit;
  font: inherit;
}

.app-text-field__input:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}

.app-text-field__footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  min-height: 16px;
}

.app-text-field__error {
  margin: 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-error);
}

.app-text-field__counter {
  margin-inline-start: auto;
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-on-surface-variant);
}

.app-text-field--error .app-text-field__input {
  border-color: var(--md-sys-color-error);
}

.app-text-field--disabled .app-text-field__input {
  opacity: 0.38;
}
</style>
