<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    resettable?: boolean;
    resetLabel?: string;
  }>(),
  { resettable: false, resetLabel: 'Filter zurücksetzen' },
);

const emit = defineEmits<{ reset: [] }>();
</script>

<template>
  <div class="filter-bar" role="search" :aria-label="label">
    <div class="filter-bar__controls">
      <slot />
    </div>
    <button v-if="resettable" type="button" class="filter-bar__reset" @click="emit('reset')">
      {{ resetLabel }}
    </button>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  min-width: 0;
  max-width: 100%;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.filter-bar__controls {
  display: flex;
  flex: 1 1 240px;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  min-width: 0;
}

.filter-bar__controls > * {
  flex: 1 1 180px;
  min-width: 0;
}

.filter-bar__reset {
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.filter-bar__reset:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
