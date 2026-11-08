<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue';

// The native dialog gives the modal behaviour: focus is trapped while open, Escape closes it, and the page below is
// inert. The component follows the open prop and reports every close (Escape, the close button) as one event.
const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    closeLabel?: string;
  }>(),
  { closeLabel: 'Schließen' },
);

const emit = defineEmits<{ close: [] }>();

const dialogRef = ref<HTMLDialogElement | null>(null);
const titleId = `${useId()}-title`;

watch(
  () => props.open,
  async (open) => {
    await nextTick();
    const dialog = dialogRef.value;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  },
  { immediate: true },
);

function onNativeClose() {
  emit('close');
}

function requestClose() {
  dialogRef.value?.close();
}
</script>

<template>
  <dialog ref="dialogRef" class="app-dialog" :aria-labelledby="titleId" @close="onNativeClose">
    <div class="app-dialog__content">
      <header class="app-dialog__header">
        <h2 :id="titleId" class="app-dialog__title">{{ title }}</h2>
        <button type="button" class="app-dialog__close" @click="requestClose">{{ closeLabel }}</button>
      </header>
      <div class="app-dialog__body">
        <slot />
      </div>
      <footer v-if="$slots.actions" class="app-dialog__actions">
        <slot name="actions" />
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.app-dialog {
  box-sizing: border-box;
  width: min(560px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  padding: 0;
  border: 0;
  border-radius: 28px;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  font-family: Roboto, system-ui, sans-serif;
}

.app-dialog::backdrop {
  background: color-mix(in srgb, var(--md-sys-color-scrim) 40%, transparent);
}

.app-dialog__content {
  display: grid;
  gap: 16px;
  padding: 24px;
  max-height: inherit;
  overflow: auto;
  box-sizing: border-box;
}

.app-dialog__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.app-dialog__title {
  margin: 0;
  min-width: 0;
  font-size: 22px;
  font-weight: 400;
  line-height: 28px;
  overflow-wrap: anywhere;
}

.app-dialog__close {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.app-dialog__body {
  min-width: 0;
  overflow-wrap: anywhere;
}

.app-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
</style>
