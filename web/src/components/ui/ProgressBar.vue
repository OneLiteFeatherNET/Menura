<script setup lang="ts">
// A determinate progress bar. The value is clamped to 0 to 100; the label names what progresses for assistive tech.
import { computed } from 'vue';

const props = defineProps<{
  value: number;
  label: string;
}>();

const percent = computed(() => Math.min(100, Math.max(0, Math.round(props.value))));
</script>

<template>
  <div class="progress-bar" role="progressbar" :aria-label="label" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="percent">
    <div class="progress-bar__fill" :style="{ width: `${percent}%` }" />
  </div>
</template>

<style scoped>
.progress-bar {
  box-sizing: border-box;
  height: 8px;
  width: 100%;
  overflow: hidden;
  border-radius: 999px;
  background: var(--md-sys-color-surface-variant);
}

.progress-bar__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--md-sys-color-primary);
  transition: width 200ms ease-out;
}

@media (prefers-reduced-motion: reduce) {
  .progress-bar__fill {
    transition: none;
  }
}
</style>
