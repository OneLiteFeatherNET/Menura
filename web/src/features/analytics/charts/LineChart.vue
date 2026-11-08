<script setup lang="ts">
import { computed } from 'vue';
import { axisMax, type Box, linePath, linePoints } from '../chart';

// A line over labelled points. Every point carries a title with its label and value, so no value is only in colour.
const props = defineProps<{
  points: { label: string; value: number | null }[];
  label: string;
  format: (value: number | null) => string;
}>();

const BOX: Box = { width: 960, height: 300, left: 64, right: 16, top: 24, bottom: 48 };

const chart = computed(() => {
  const values = props.points.map((point) => point.value).filter((value): value is number => value !== null);
  const max = axisMax(values);
  const coords = linePoints(
    props.points.map((point) => point.value),
    max,
    BOX,
  );
  const plotHeight = BOX.height - BOX.top - BOX.bottom;
  const every = Math.max(1, Math.ceil(props.points.length / 8));
  return {
    path: linePath(coords),
    coords,
    gridLines: [0, 0.5, 1].map((fraction) => ({
      y: BOX.top + plotHeight - fraction * plotHeight,
      label: props.format(fraction * max),
    })),
    labels: props.points
      .map((point, index) => ({ label: point.label, x: coords[index]?.x ?? null, index }))
      .filter((entry) => entry.index % every === 0 && entry.x !== null),
  };
});
</script>

<template>
  <svg class="chart" :viewBox="`0 0 ${BOX.width} ${BOX.height}`" role="img" :aria-label="label">
    <g v-for="line in chart.gridLines" :key="line.y">
      <line :x1="BOX.left" :x2="BOX.width - BOX.right" :y1="line.y" :y2="line.y" class="grid" />
      <text :x="BOX.left - 10" :y="line.y + 5" text-anchor="end" class="axis">{{ line.label }}</text>
    </g>
    <path v-if="chart.path" :d="chart.path" class="line" />
    <g v-for="(point, index) in props.points" :key="`${point.label}-${index}`">
      <circle
        v-if="chart.coords[index]"
        :cx="chart.coords[index]!.x"
        :cy="chart.coords[index]!.y"
        r="4"
        class="dot"
      >
        <title>{{ point.label }}: {{ format(point.value) }}</title>
      </circle>
    </g>
    <text
      v-for="entry in chart.labels"
      :key="`x-${entry.index}`"
      :x="entry.x ?? 0"
      :y="BOX.height - 16"
      text-anchor="middle"
      class="axis"
    >
      {{ entry.label }}
    </text>
  </svg>
</template>

<style scoped>
.chart {
  display: block;
  width: 100%;
  height: auto;
}

.grid {
  stroke: var(--app-border);
  stroke-width: 1;
}

.axis {
  fill: var(--app-muted);
  font-size: 15px;
}

.line {
  fill: none;
  stroke: var(--md-sys-color-primary);
  stroke-width: 3;
  stroke-linejoin: round;
}

.dot {
  fill: var(--md-sys-color-primary);
}
</style>
