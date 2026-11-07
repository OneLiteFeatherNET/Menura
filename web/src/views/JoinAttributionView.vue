<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { api } from '../api/client';
import type { Platform } from '../api/types';
import LoadState from '../components/LoadState.vue';
import { useAsync } from '../composables/useAsync';
import { groupedBars, PLATFORM_ORDER, weeksOf } from '../domain/attribution';
import { formatCount, PLATFORM_LABELS } from '../domain/format';

// Bar colours are M3 role tokens. Every bar also carries its value and the legend names it.
const PLATFORM_COLOR: Record<Platform, string> = {
  youtube: 'var(--md-sys-color-primary)',
  tiktok: 'var(--md-sys-color-tertiary)',
  instagram: 'var(--md-sys-color-secondary)',
  direct: 'var(--md-sys-color-outline)',
};

const CHART = { width: 960, height: 340, left: 56, right: 16, top: 32, bottom: 48 };

const { data, error, loading, run } = useAsync(async () => {
  const [channels, weekly] = await Promise.all([api.channels(), api.weeklyAttribution()]);
  return { channels, weekly };
});

onMounted(run);

const chart = computed(() => {
  if (!data.value) {
    return null;
  }
  const weeks = weeksOf(data.value.weekly);
  const { bars, maxValue } = groupedBars(weeks, data.value.weekly, CHART);
  const plotHeight = CHART.height - CHART.top - CHART.bottom;
  const groupWidth = weeks.length === 0 ? 0 : (CHART.width - CHART.left - CHART.right) / weeks.length;
  return {
    weeks,
    bars,
    maxValue,
    gridLines: [0, 0.5, 1].map((fraction) => ({
      y: CHART.top + plotHeight - fraction * plotHeight,
      label: formatCount(Math.round(fraction * maxValue)),
    })),
    weekLabels: weeks.map((week, index) => ({
      week,
      x: CHART.left + index * groupWidth + groupWidth / 2,
    })),
  };
});
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Join-Attribution</h2>
        <p class="page-lead">Woher die neuen Spieler kommen, pro Kanal und Woche.</p>
      </div>
    </header>

    <LoadState :loading="loading" :error="error">
      <template v-if="data && chart">
        <section class="section">
          <h3 class="section-title">Kanäle</h3>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Plattform</th>
                  <th>Join-Host</th>
                  <th>CTA (Deutsch)</th>
                  <th>CTA (Englisch)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="channel in data.channels" :key="channel.id">
                  <td>{{ PLATFORM_LABELS[channel.platform] }}</td>
                  <td><code>{{ channel.joinHost }}</code></td>
                  <td>{{ channel.ctaText.de }}</td>
                  <td>{{ channel.ctaText.en }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="section">
          <h3 class="section-title">Neue Spieler pro Woche</h3>
          <ul class="legend" aria-label="Legende">
            <li v-for="platform in PLATFORM_ORDER" :key="platform">
              <span class="swatch" :style="{ background: PLATFORM_COLOR[platform] }"></span>
              {{ PLATFORM_LABELS[platform] }}
            </li>
          </ul>

          <div class="chart-card card">
            <svg
              class="chart"
              :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
              role="img"
              aria-label="Gemessene Erstjoins pro Woche und Plattform"
            >
              <g v-for="line in chart.gridLines" :key="line.y">
                <line
                  :x1="CHART.left"
                  :x2="CHART.width - CHART.right"
                  :y1="line.y"
                  :y2="line.y"
                  class="grid"
                />
                <text :x="CHART.left - 10" :y="line.y + 5" text-anchor="end" class="axis">{{ line.label }}</text>
              </g>

              <g v-for="bar in chart.bars" :key="`${bar.week}-${bar.platform}`">
                <rect
                  :x="bar.x"
                  :y="bar.y"
                  :width="bar.width - 4"
                  :height="Math.max(bar.height, 0)"
                  rx="4"
                  :style="{ fill: PLATFORM_COLOR[bar.platform] }"
                >
                  <title>{{ bar.week }} · {{ PLATFORM_LABELS[bar.platform] }}: {{ bar.value }}</title>
                </rect>
                <text
                  :x="bar.x + (bar.width - 4) / 2"
                  :y="bar.y - 6"
                  text-anchor="middle"
                  class="value"
                >{{ formatCount(bar.value) }}</text>
              </g>

              <text
                v-for="label in chart.weekLabels"
                :key="label.week"
                :x="label.x"
                :y="CHART.height - 16"
                text-anchor="middle"
                class="axis"
              >{{ label.week }}</text>
            </svg>
          </div>

          <p class="explainer">Plattform exakt gemessen, Clip und Serie geschätzt (72-h-Fenster).</p>
        </section>
      </template>
    </LoadState>
  </div>
</template>

<style scoped>
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin: 0;
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

.chart-card {
  padding: 16px 20px;
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
  font-size: 16px;
}

.value {
  fill: var(--md-sys-color-on-surface);
  font-size: 18px;
  font-weight: 500;
}

.explainer {
  margin: 0;
  font-size: 16px;
  color: var(--app-muted);
}

code {
  font-size: 15px;
}
</style>
