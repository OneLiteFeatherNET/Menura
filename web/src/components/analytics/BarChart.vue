<script setup lang="ts">
import { computed } from 'vue';
import { NOT_AVAILABLE, barFraction, formatNumber, maxValue } from '../../analytics/format';

// A horizontal bar per item, scaled to the largest value. A null value (the platform does not provide the metric) has
// no bar and reads 'nicht verfügbar'. The chart only draws the data it gets; it never fetches.
interface BarItem {
  label: string;
  value: number | null;
}

const props = withDefaults(
  defineProps<{
    caption: string;
    items: BarItem[];
    emptyText?: string;
  }>(),
  { emptyText: 'Keine Daten.' },
);

const max = computed(() => maxValue(props.items.map((item) => item.value)));

function valueText(item: BarItem): string {
  return item.value === null ? NOT_AVAILABLE : formatNumber(item.value);
}

// The bars are hidden from assistive technology; the image's name lists every bar with its value instead.
const summary = computed(() => `${props.caption}: ${props.items.map((item) => `${item.label} ${valueText(item)}`).join('; ')}`);
</script>

<template>
  <p v-if="items.length === 0" class="bar-chart__empty">{{ emptyText }}</p>
  <figure v-else class="bar-chart">
    <figcaption class="bar-chart__caption">{{ caption }}</figcaption>
    <div class="bar-chart__rows" role="img" :aria-label="summary">
      <div v-for="(item, index) in items" :key="index" class="bar-chart__row" aria-hidden="true">
        <span class="bar-chart__label">{{ item.label }}</span>
        <span v-if="item.value !== null" class="bar-chart__track">
          <span class="bar-chart__bar" :style="{ width: `${barFraction(item.value, max) * 100}%` }"></span>
        </span>
        <span v-else class="bar-chart__track bar-chart__track--empty"></span>
        <span class="bar-chart__value" :class="{ 'bar-chart__value--missing': item.value === null }">{{ valueText(item) }}</span>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.bar-chart {
  display: grid;
  gap: 8px;
  margin: 0;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.bar-chart__caption,
.bar-chart__empty {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.bar-chart__rows {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.bar-chart__row {
  display: grid;
  grid-template-columns: minmax(96px, 30%) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  font-size: 14px;
}

.bar-chart__label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.bar-chart__track {
  display: block;
  height: 16px;
  min-width: 0;
  border-radius: 4px;
  background: var(--md-sys-color-surface-variant);
  overflow: hidden;
}

.bar-chart__track--empty {
  background: repeating-linear-gradient(
    135deg,
    var(--md-sys-color-surface-variant),
    var(--md-sys-color-surface-variant) 4px,
    transparent 4px,
    transparent 8px
  );
}

.bar-chart__bar {
  display: block;
  height: 100%;
  min-width: 2px;
  border-radius: 4px;
  background: var(--md-sys-color-primary);
}

.bar-chart__value {
  font-variant-numeric: tabular-nums;
  text-align: end;
  overflow-wrap: anywhere;
}

.bar-chart__value--missing {
  color: var(--md-sys-color-on-surface-variant);
  font-style: italic;
}
</style>
