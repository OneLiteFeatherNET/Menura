<script setup lang="ts">
import { computed } from 'vue';
import { axisMax, type Box, categoryWidth, SERIES_COLORS } from '../chart';

// Grouped bars: one group per category, one bar per series. A null value draws no bar; every bar has a title.
const props = defineProps<{
  categories: { label: string; values: (number | null)[] }[];
  seriesNames: string[];
  label: string;
  format: (value: number | null) => string;
}>();

const BOX: Box = { width: 960, height: 320, left: 64, right: 16, top: 24, bottom: 56 };

const chart = computed(() => {
  const all = props.categories.flatMap((category) => category.values).filter((v): v is number => v !== null);
  const max = axisMax(all);
  const plotHeight = BOX.height - BOX.top - BOX.bottom;
  const groupWidth = categoryWidth(props.categories.length, BOX);
  const barWidth = props.seriesNames.length === 0 ? 0 : (groupWidth * 0.8) / props.seriesNames.length;
  const bars = props.categories.flatMap((category, categoryIndex) =>
    category.values.flatMap((value, seriesIndex) => {
      if (value === null) {
        return [];
      }
      const height = (value / max) * plotHeight;
      return [
        {
          key: `${categoryIndex}-${seriesIndex}`,
          x: BOX.left + categoryIndex * groupWidth + groupWidth * 0.1 + seriesIndex * barWidth,
          y: BOX.top + plotHeight - height,
          width: Math.max(barWidth - 2, 1),
          height,
          color: SERIES_COLORS[seriesIndex % SERIES_COLORS.length],
          title: `${category.label} · ${props.seriesNames[seriesIndex] ?? ''}: ${props.format(value)}`,
        },
      ];
    }),
  );
  return {
    bars,
    gridLines: [0, 0.5, 1].map((fraction) => ({
      y: BOX.top + plotHeight - fraction * plotHeight,
      label: props.format(fraction * max),
    })),
    labels: props.categories.map((category, index) => ({
      label: category.label,
      x: BOX.left + index * groupWidth + groupWidth / 2,
    })),
  };
});
</script>

<template>
  <div>
    <ul v-if="seriesNames.length > 1" class="legend" aria-label="Reihen">
      <li v-for="(name, index) in seriesNames" :key="name">
        <span class="swatch" :style="{ background: SERIES_COLORS[index % SERIES_COLORS.length] }"></span>
        {{ name }}
      </li>
    </ul>
    <svg class="chart" :viewBox="`0 0 ${BOX.width} ${BOX.height}`" role="img" :aria-label="label">
      <g v-for="line in chart.gridLines" :key="line.y">
        <line :x1="BOX.left" :x2="BOX.width - BOX.right" :y1="line.y" :y2="line.y" class="grid" />
        <text :x="BOX.left - 10" :y="line.y + 5" text-anchor="end" class="axis">{{ line.label }}</text>
      </g>
      <rect
        v-for="bar in chart.bars"
        :key="bar.key"
        :x="bar.x"
        :y="bar.y"
        :width="bar.width"
        :height="Math.max(bar.height, 0)"
        rx="3"
        :style="{ fill: bar.color }"
      >
        <title>{{ bar.title }}</title>
      </rect>
      <text
        v-for="label in chart.labels"
        :key="label.label"
        :x="label.x"
        :y="BOX.height - 20"
        text-anchor="middle"
        class="axis"
      >
        {{ label.label }}
      </text>
    </svg>
  </div>
</template>

<style scoped>
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
  font-size: 16px;
}

.legend li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.swatch {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-block;
}

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
</style>
