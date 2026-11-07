<script setup lang="ts">
import { formatJoins } from '../domain/format';

// First joins are measured (exact) for the week and estimated for posts and series. Estimates
// always carry the "geschätzt" chip so nobody reads them as measured.
defineProps<{
  value: number | null;
  estimated: boolean;
}>();
</script>

<template>
  <span class="joins">
    <span v-if="value === null">–</span>
    <span v-else class="joins-value">{{ formatJoins(value) }}</span>
    <span v-if="estimated && value !== null" class="estimate-chip">geschätzt</span>
  </span>
</template>

<style scoped>
.joins {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.estimate-chip {
  font-size: 13px;
  line-height: 20px;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  color: var(--md-sys-color-on-surface-variant);
}
</style>
