<script setup lang="ts">
// A responsive grid of cards: the columns fill the width and shrink to one column on a phone. The caller passes one
// li per card; the grid shows emptyText when the slot holds no card.
defineProps<{
  label: string;
  emptyText?: string;
}>();
</script>

<template>
  <ul v-if="$slots.default" class="card-grid" :aria-label="label">
    <slot />
  </ul>
  <p v-else-if="emptyText" class="card-grid__empty">{{ emptyText }}</p>
</template>

<style scoped>
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
}

.card-grid :deep(> li) {
  box-sizing: border-box;
  min-width: 0;
  padding: 16px;
  border-radius: 12px;
  background: var(--md-sys-color-surface-container, var(--md-sys-color-surface));
  color: var(--md-sys-color-on-surface);
  overflow-wrap: anywhere;
}

.card-grid__empty {
  margin: 0;
  font-family: Roboto, system-ui, sans-serif;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
