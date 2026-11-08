<script setup lang="ts">
// The actions wrap below the title when the width is not enough, so they never leave the viewport (a prototype
// header ran past the right edge at phone width). Every box may shrink (min-width: 0), and long words break.
defineProps<{
  title: string;
  subtitle?: string;
}>();
</script>

<template>
  <header class="page-header">
    <div class="page-header__text">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.default" class="page-header__actions">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  box-sizing: border-box;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  max-width: 100%;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.page-header__text {
  flex: 1 1 280px;
  min-width: 0;
}

.page-header__title {
  margin: 0;
  font-size: 22px;
  font-weight: 400;
  line-height: 28px;
  overflow-wrap: anywhere;
}

.page-header__subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}
</style>
