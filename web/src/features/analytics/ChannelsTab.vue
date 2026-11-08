<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../../api/client';
import type { Channel } from '../../api/types';
import LoadState from '../../components/LoadState.vue';
import { PLATFORM_LABELS } from '../../domain/format';
import { analyticsApi } from './api';
import BarChart from './charts/BarChart.vue';
import { formatMetricValue, isAverageMetric } from './format';
import type { AggregateResult, Metric } from './types';

const props = defineProps<{ channelIds: string[] }>();

/** The measurement offset of the channel view: every post is read at +24 h. */
const OFFSET = '+24h';

const mode = ref<'single' | 'compare'>('single');
const chosen = ref<string | null>(null);
const metricId = ref('views');
const metrics = ref<Metric[]>([]);
const channels = ref<Channel[]>([]);
const results = ref<Map<string, AggregateResult>>(new Map());
const loading = ref(true);
const error = ref<string | null>(null);
let latest = 0;

const targets = computed<string[]>(() => {
  if (mode.value === 'compare') {
    return [...props.channelIds];
  }
  const id = chosen.value !== null && props.channelIds.includes(chosen.value) ? chosen.value : props.channelIds[0];
  return id === undefined ? [] : [id];
});

function nameOf(id: string): string {
  const channel = channels.value.find((candidate) => candidate.id === id);
  return channel ? PLATFORM_LABELS[channel.platform] : id;
}

function valueOf(id: string, week: string): number | null {
  const row = results.value.get(id)?.rows.find((candidate) => candidate.key === week);
  return row ? row.value : null;
}

/** The label of a cell: the value, "nicht verfügbar" when the platform lacks the metric, or no posts. */
function cellText(id: string, week: string): string {
  const result = results.value.get(id);
  if (result?.missing.includes(id)) {
    return 'nicht verfügbar';
  }
  const value = valueOf(id, week);
  return value === null ? 'keine Beiträge' : formatMetricValue(metricId.value, value);
}

const weeks = computed<string[]>(() => {
  const keys = new Set<string>();
  for (const result of results.value.values()) {
    for (const row of result.rows) {
      keys.add(row.key);
    }
  }
  return [...keys].sort();
});

const chart = computed(() => ({
  categories: weeks.value.map((week) => ({
    label: week,
    values: targets.value.map((id) => valueOf(id, week)),
  })),
  seriesNames: targets.value.map(nameOf),
}));

async function load(): Promise<void> {
  const request = ++latest;
  const ids = targets.value;
  loading.value = true;
  error.value = null;
  try {
    const answers = await Promise.all(
      ids.map((id) =>
        analyticsApi.aggregate({
          metric: metricId.value,
          channelIds: [id],
          groupBy: 'week',
          offset: OFFSET,
        }),
      ),
    );
    if (request === latest) {
      const byChannel = new Map<string, AggregateResult>();
      ids.forEach((id, index) => {
        const answer = answers[index];
        if (answer) {
          byChannel.set(id, answer);
        }
      });
      results.value = byChannel;
    }
  } catch (cause) {
    if (request === latest) {
      error.value = cause instanceof Error ? cause.message : 'Unerwarteter Fehler.';
    }
  } finally {
    if (request === latest) {
      loading.value = false;
    }
  }
}

onMounted(async () => {
  const [allChannels, allMetrics] = await Promise.all([api.channels(), analyticsApi.metrics()]);
  channels.value = allChannels;
  metrics.value = allMetrics;
  await load();
});

watch(() => [props.channelIds.join(','), mode.value, chosen.value, metricId.value], load);

const explainer = computed(() =>
  isAverageMetric(metricId.value)
    ? 'Mittel je Kalenderwoche (Berlin), jeweils der Stand nach +24 h.'
    : 'Summe je Kalenderwoche (Berlin), jeweils der Stand nach +24 h.',
);
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Kanäle</h2>
        <p class="page-lead">Ein Kanal für sich oder alle ausgewählten Kanäle nebeneinander, Woche für Woche.</p>
      </div>
    </header>

    <p v-if="channelIds.length === 0" class="card">Kein Kanal ausgewählt. Wähle oben mindestens einen Kanal.</p>

    <template v-else>
      <div class="controls">
        <div class="segmented" role="group" aria-label="Ansicht">
          <button
            type="button"
            class="segment"
            :aria-pressed="mode === 'single' ? 'true' : 'false'"
            @click="mode = 'single'"
          >
            Einzelkanal
          </button>
          <button
            type="button"
            class="segment"
            :aria-pressed="mode === 'compare' ? 'true' : 'false'"
            @click="mode = 'compare'"
          >
            Vergleich
          </button>
        </div>

        <label v-if="mode === 'single'" class="field">
          <span>Kanal</span>
          <select v-model="chosen">
            <option v-for="id in channelIds" :key="id" :value="id">{{ nameOf(id) }}</option>
          </select>
        </label>

        <label class="field">
          <span>Kennzahl</span>
          <select v-model="metricId">
            <option v-for="metric in metrics" :key="metric.id" :value="metric.id">{{ metric.label }}</option>
          </select>
        </label>
      </div>

      <LoadState :loading="loading" :error="error">
        <section v-if="weeks.length > 0" class="section">
          <h3 class="section-title">Pro Kalenderwoche</h3>
          <div class="card chart-card">
            <BarChart
              :categories="chart.categories"
              :series-names="chart.seriesNames"
              :label="`Kennzahl pro Woche: ${metrics.find((m) => m.id === metricId)?.label ?? metricId}`"
              :format="(value) => formatMetricValue(metricId, value)"
            />
          </div>
          <p class="explainer">{{ explainer }}</p>

          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Woche</th>
                  <th v-for="id in targets" :key="id" class="num">{{ nameOf(id) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="week in weeks" :key="week">
                  <td>{{ week }}</td>
                  <td v-for="id in targets" :key="id" class="num">{{ cellText(id, week) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <p v-else class="card">Keine gemessenen Beiträge für diese Auswahl.</p>
      </LoadState>
    </template>
  </div>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 24px;
}

.segmented {
  display: inline-flex;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 8px;
  overflow: hidden;
}

.segment {
  min-height: 40px;
  padding: 0 16px;
  border: 0;
  background: transparent;
  color: var(--md-sys-color-on-surface);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.segment[aria-pressed='true'] {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 15px;
  color: var(--app-muted);
}

.field select {
  min-height: 40px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  font: inherit;
}

.chart-card {
  padding: 16px 20px;
}

.explainer {
  margin: 12px 0 0;
  font-size: 16px;
  color: var(--app-muted);
}
</style>
