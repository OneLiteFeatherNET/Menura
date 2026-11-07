<script setup lang="ts">
import { formatViewPercent } from '../domain/format';

defineProps<{
  value: number | null;
}>();
</script>

<template>
  <span :class="{ 'not-available': value === null }">{{ formatViewPercent(value) }}</span>
</template>

<style scoped>
.not-available {
  color: var(--md-sys-color-on-surface-variant);
  font-style: italic;
}
</style>
