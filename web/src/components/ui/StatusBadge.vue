<script setup lang="ts">
// The meaning is carried by the text, and the tone only adds colour, so the badge reads the same without colour.
withDefaults(
  defineProps<{
    label: string;
    tone?: 'neutral' | 'info' | 'success' | 'warning' | 'error';
  }>(),
  { tone: 'neutral' },
);
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">{{ label }}</span>
</template>

<style scoped>
.status-badge {
  display: inline-block;
  max-width: 100%;
  box-sizing: border-box;
  padding: 2px 10px;
  border-radius: 999px;
  font-family: Roboto, system-ui, sans-serif;
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
  background: var(--md-sys-color-surface-variant);
  color: var(--md-sys-color-on-surface-variant);
}

.status-badge--info {
  background: var(--md-sys-color-primary-container);
  color: var(--md-sys-color-on-primary-container);
}

.status-badge--success {
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
}

.status-badge--warning {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.status-badge--error {
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
}
</style>
