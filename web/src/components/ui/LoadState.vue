<script setup lang="ts">
// Shows the state of a list or panel: loading, error (with a retry), empty, or the content in the default slot.
withDefaults(
  defineProps<{
    status: 'loading' | 'error' | 'empty' | 'ready';
    loadingLabel?: string;
    errorMessage?: string;
    emptyText?: string;
    retryLabel?: string;
  }>(),
  {
    loadingLabel: 'Lädt …',
    errorMessage: 'Etwas ist schiefgelaufen.',
    emptyText: 'Keine Einträge.',
    retryLabel: 'Erneut versuchen',
  },
);

const emit = defineEmits<{ retry: [] }>();
</script>

<template>
  <p v-if="status === 'loading'" role="status" class="load-state load-state__text">{{ loadingLabel }}</p>
  <div v-else-if="status === 'error'" role="alert" class="load-state load-state__error">
    <p class="load-state__text">{{ errorMessage }}</p>
    <button type="button" class="load-state__retry" @click="emit('retry')">{{ retryLabel }}</button>
  </div>
  <p v-else-if="status === 'empty'" class="load-state load-state__text">{{ emptyText }}</p>
  <slot v-else />
</template>

<style scoped>
.load-state {
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.load-state__text {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.load-state__error {
  display: grid;
  justify-items: start;
  gap: 8px;
  color: var(--md-sys-color-error);
}

.load-state__retry {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.load-state__retry:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
