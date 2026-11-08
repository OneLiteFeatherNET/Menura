<script setup lang="ts">
// A confirmation shown in place of the action, not in a dialog, so the user keeps the context of the row.
withDefaults(
  defineProps<{
    message: string;
    confirmLabel: string;
    cancelLabel: string;
    tone?: 'default' | 'danger';
    busy?: boolean;
  }>(),
  { tone: 'default', busy: false },
);

const emit = defineEmits<{ confirm: []; cancel: [] }>();
</script>

<template>
  <div class="confirm-inline" :class="`confirm-inline--${tone}`">
    <p class="confirm-inline__message">{{ message }}</p>
    <div class="confirm-inline__actions">
      <button type="button" class="confirm-inline__confirm" :disabled="busy" @click="emit('confirm')">
        {{ confirmLabel }}
      </button>
      <button type="button" class="confirm-inline__cancel" :disabled="busy" @click="emit('cancel')">
        {{ cancelLabel }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.confirm-inline {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--md-sys-color-surface-variant);
  color: var(--md-sys-color-on-surface);
  font-family: Roboto, system-ui, sans-serif;
  font-size: 14px;
}

.confirm-inline__message {
  margin: 0;
  overflow-wrap: anywhere;
}

.confirm-inline__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.confirm-inline__confirm,
.confirm-inline__cancel {
  min-height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  font: inherit;
  cursor: pointer;
}

.confirm-inline__confirm {
  border: 0;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
}

.confirm-inline--danger .confirm-inline__confirm {
  background: var(--md-sys-color-error);
  color: var(--md-sys-color-on-error);
}

.confirm-inline__cancel {
  border: 1px solid var(--md-sys-color-outline);
  background: transparent;
  color: var(--md-sys-color-primary);
}

.confirm-inline__confirm:disabled,
.confirm-inline__cancel:disabled {
  opacity: 0.38;
  cursor: default;
}

.confirm-inline__confirm:focus-visible,
.confirm-inline__cancel:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
