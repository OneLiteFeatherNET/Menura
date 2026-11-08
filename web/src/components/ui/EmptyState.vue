<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
}>();
</script>

<template>
  <section class="empty-state">
    <h2 class="empty-state__title">{{ title }}</h2>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.empty-state {
  display: grid;
  justify-items: start;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
  padding: 24px;
  box-sizing: border-box;
  border-radius: 16px;
  background: var(--md-sys-color-surface-container, var(--md-sys-color-surface));
  color: var(--md-sys-color-on-surface);
  font-family: Roboto, system-ui, sans-serif;
}

.empty-state__title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  line-height: 24px;
  overflow-wrap: anywhere;
}

.empty-state__description {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.empty-state__action {
  margin-top: 8px;
}
</style>
