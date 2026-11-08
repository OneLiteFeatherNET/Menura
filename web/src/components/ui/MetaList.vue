<script setup lang="ts">
defineProps<{
  items: { label: string; value: string }[];
  emptyText?: string;
}>();
</script>

<template>
  <dl v-if="items.length" class="meta-list">
    <div v-for="item in items" :key="item.label" class="meta-list__row">
      <dt class="meta-list__label">{{ item.label }}</dt>
      <dd class="meta-list__value">{{ item.value }}</dd>
    </div>
  </dl>
  <p v-else-if="emptyText" class="meta-list__empty">{{ emptyText }}</p>
</template>

<style scoped>
.meta-list {
  display: grid;
  grid-template-columns: minmax(0, max-content) minmax(0, 1fr);
  gap: 8px 16px;
  margin: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.meta-list__row {
  display: contents;
}

.meta-list__label {
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
}

.meta-list__value {
  margin: 0;
  min-width: 0;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.meta-list__empty {
  margin: 0;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
