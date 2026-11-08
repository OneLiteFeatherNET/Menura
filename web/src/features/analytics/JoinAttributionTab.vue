<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { api } from '../../api/client';
import type { JoinSource } from '../../api/types';
import LoadState from '../../components/LoadState.vue';
import { useAsync } from '../../composables/useAsync';
import { SOURCE_ORDER, stackedColumns, summaryOf, weeksOf, type WeekSummary } from '../../domain/attribution';
import { formatCount, PLATFORM_LABELS } from '../../domain/format';

// Segment colours are M3 role tokens. Every segment also carries its value and the legend names it.
const SOURCE_COLOR: Record<JoinSource, string> = {
  youtube: 'var(--md-sys-color-primary)',
  tiktok: 'var(--md-sys-color-tertiary)',
  instagram: 'var(--md-sys-color-secondary)',
  twitch: 'var(--md-sys-color-primary-container)',
  friends: 'var(--md-sys-color-tertiary-container)',
  other: 'var(--md-sys-color-secondary-container)',
  unanswered: 'var(--md-sys-color-outline)',
};

const CHART = { width: 960, height: 340, left: 56, right: 16, top: 32, bottom: 64 };

const { data, error, loading, run } = useAsync(() => api.weeklyAttribution());

onMounted(run);

function percent(rate: number): string {
  return `${Math.round(rate * 100)} %`;
}

const summaries = computed<WeekSummary[]>(() => {
  const rows = data.value ?? [];
  return weeksOf(rows)
    .map((week) => summaryOf(rows, week))
    .filter((summary): summary is WeekSummary => summary !== null);
});

const chart = computed(() => {
  const rows = data.value;
  if (!rows) {
    return null;
  }
  const weeks = weeksOf(rows);
  const { segments, maxValue } = stackedColumns(weeks, rows, CHART);
  const plotHeight = CHART.height - CHART.top - CHART.bottom;
  const groupWidth = weeks.length === 0 ? 0 : (CHART.width - CHART.left - CHART.right) / weeks.length;
  return {
    segments,
    gridLines: [0, 0.5, 1].map((fraction) => ({
      y: CHART.top + plotHeight - fraction * plotHeight,
      label: formatCount(Math.round(fraction * maxValue)),
    })),
    weekLabels: summaries.value.map((summary, index) => ({
      week: summary.week,
      rate: percent(summary.answerRate),
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
        <p class="page-lead">Erstjoins pro Woche und, soweit die Spieler es nennen, woher sie kommen.</p>
      </div>
    </header>

    <LoadState :loading="loading" :error="error">
      <template v-if="data && chart">
        <section class="section">
          <h3 class="section-title">Erstjoins pro Woche</h3>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Woche</th>
                  <th class="num">Erstjoins gesamt</th>
                  <th class="num">Antworten</th>
                  <th class="num">Antwortrate</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="summary in summaries" :key="summary.week">
                  <td>{{ summary.week }}</td>
                  <td class="num">{{ formatCount(summary.firstJoins) }}</td>
                  <td class="num">{{ formatCount(summary.answered) }} von {{ formatCount(summary.firstJoins) }}</td>
                  <td class="num">{{ percent(summary.answerRate) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="section">
          <h3 class="section-title">Angegebene Quelle, laut Spielern</h3>
          <ul class="legend" aria-label="Quellen laut Spielern">
            <li v-for="source in SOURCE_ORDER" :key="source">
              <span class="swatch" :style="{ background: SOURCE_COLOR[source] }"></span>
              {{ PLATFORM_LABELS[source] }}
            </li>
          </ul>

          <div class="chart-card card">
            <svg
              class="chart"
              :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
              role="img"
              aria-label="Erstjoins pro Woche nach Angabe der Spieler"
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

              <g v-for="segment in chart.segments" :key="`${segment.week}-${segment.source}`">
                <rect
                  :x="segment.x"
                  :y="segment.y"
                  :width="segment.width"
                  :height="Math.max(segment.height, 0)"
                  rx="4"
                  :style="{ fill: SOURCE_COLOR[segment.source] }"
                >
                  <title>{{ segment.week }} · {{ PLATFORM_LABELS[segment.source] }} (laut Spielern): {{ segment.value }}</title>
                </rect>
              </g>

              <g v-for="label in chart.weekLabels" :key="label.week">
                <text :x="label.x" :y="CHART.height - 30" text-anchor="middle" class="axis">{{ label.week }}</text>
                <text :x="label.x" :y="CHART.height - 10" text-anchor="middle" class="axis">
                  Antwortrate {{ label.rate }}
                </text>
              </g>
            </svg>
          </div>

          <p class="explainer">
            Gemessen: Erstjoins gesamt und Antworten der Ingame-Frage. Geschätzt: Verteilung auf Posts im 72-h-Fenster.
          </p>
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

.explainer {
  margin: 0;
  font-size: 16px;
  color: var(--app-muted);
}
</style>
