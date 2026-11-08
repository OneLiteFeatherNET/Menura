<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../../api/client';
import LoadState from '../../components/LoadState.vue';
import { weekdayLabel } from '../../domain/format';
import { analyticsApi } from './api';
import BarChart from './charts/BarChart.vue';
import { heatBackground, heatLevel } from './chart';
import { formatMetricValue, unavailableNote } from './format';
import type { AggregateRow, Heatmap, HeatmapCell, Metric, OffsetLabel } from './types';
import { WEEKDAYS } from './types';

const props = defineProps<{ channelIds: string[] }>();

const HOURS = Array.from({ length: 24 }, (_, hour) => hour);
const WEEKDAY_SHORT: Record<string, string> = {
  MONDAY: 'Mo',
  TUESDAY: 'Di',
  WEDNESDAY: 'Mi',
  THURSDAY: 'Do',
  FRIDAY: 'Fr',
  SATURDAY: 'Sa',
  SUNDAY: 'So',
};
const OFFSET_OPTIONS: OffsetLabel[] = ['+24h', '+48h', '+7d'];

const metricId = ref('engagedViews');
const offset = ref<OffsetLabel>('+24h');
const metrics = ref<Metric[]>([]);
const heatmap = ref<Heatmap | null>(null);
const hourRows = ref<AggregateRow[]>([]);
const missing = ref<string[]>([]);
const platformOf = ref<Record<string, string>>({});
const loading = ref(true);
const error = ref<string | null>(null);
let latest = 0;

const metricLabel = computed(() => metrics.value.find((m) => m.id === metricId.value)?.label ?? metricId.value);

const cellsByKey = computed(() => {
  const map = new Map<string, HeatmapCell>();
  for (const cell of heatmap.value?.cells ?? []) {
    map.set(`${cell.weekday}-${cell.hour}`, cell);
  }
  return map;
});

const maxValue = computed(() => Math.max(0, ...(heatmap.value?.cells ?? []).map((cell) => cell.value)));

/** The accessible name of one cell, for example "Dienstag 17 Uhr: 830 Engaged Views, 2 Posts". */
function cellLabel(weekday: string, hour: number): string {
  const day = weekdayLabel(weekday);
  const cell = cellsByKey.value.get(`${weekday}-${hour}`);
  if (!cell) {
    return `${day} ${hour} Uhr: keine Beiträge`;
  }
  const posts = cell.posts === 1 ? '1 Post' : `${cell.posts} Posts`;
  return `${day} ${hour} Uhr: ${formatMetricValue(metricId.value, cell.value)} ${metricLabel.value}, ${posts}`;
}

function cellFill(weekday: string, hour: number): string {
  const cell = cellsByKey.value.get(`${weekday}-${hour}`);
  return heatBackground(heatLevel(cell?.value ?? null, maxValue.value));
}

const hourChart = computed(() => {
  const byHour = new Map(hourRows.value.map((row) => [Number(row.key), row.value]));
  return {
    categories: HOURS.map((hour) => ({ label: String(hour), values: [byHour.get(hour) ?? null] })),
    hasRows: hourRows.value.length > 0,
  };
});

async function load(): Promise<void> {
  const request = ++latest;
  loading.value = true;
  error.value = null;
  const channelIds = [...props.channelIds];
  try {
    const [grid, hours] = await Promise.all([
      analyticsApi.heatmap({ metric: metricId.value, channelIds, offset: offset.value }),
      analyticsApi.aggregate({ metric: metricId.value, channelIds, groupBy: 'publishHour', offset: offset.value }),
    ]);
    if (request === latest) {
      heatmap.value = grid;
      hourRows.value = hours.rows;
      missing.value = grid.missing;
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
  const [allMetrics, channels] = await Promise.all([analyticsApi.metrics(), api.channels()]);
  metrics.value = allMetrics;
  platformOf.value = Object.fromEntries(channels.map((channel) => [channel.id, channel.platform]));
  await load();
});

watch(() => [props.channelIds.join(','), metricId.value, offset.value], load);

const unavailable = computed(() => {
  const selected = props.channelIds;
  return selected.length > 0 && missing.value.length >= selected.length;
});

const partialNote = computed(() => {
  if (unavailable.value || missing.value.length === 0) {
    return '';
  }
  return unavailableNote(missing.value.map((id) => platformOf.value[id] ?? id));
});
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Uhrzeit &amp; Wochentag</h2>
        <p class="page-lead">Wann veröffentlichte Beiträge am besten laufen, nach Wochentag und Stunde (Berlin).</p>
      </div>
    </header>

    <p v-if="channelIds.length === 0" class="card">Kein Kanal ausgewählt. Wähle oben mindestens einen Kanal.</p>

    <template v-else>
      <div class="controls">
        <label class="field">
          <span>Kennzahl</span>
          <select v-model="metricId">
            <option v-for="metric in metrics" :key="metric.id" :value="metric.id">{{ metric.label }}</option>
          </select>
        </label>
        <label class="field">
          <span>Messstand</span>
          <select v-model="offset">
            <option v-for="option in OFFSET_OPTIONS" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
      </div>

      <p class="note">
        Plattformen liefern keine Stundenwerte. Menura misst selbst nach +1 h, +2 h, +4 h, +8 h, +24 h, +48 h und
        +7 d und vergleicht gleich alte Messstände.
      </p>

      <LoadState :loading="loading" :error="error">
        <p v-if="unavailable" class="card">
          Die Kennzahl ist für die gewählten Kanäle nicht verfügbar.
        </p>
        <template v-else>
          <p v-if="partialNote" class="kpi-note">{{ partialNote }}</p>

          <section class="section">
            <h3 class="section-title">Wochentag und Stunde</h3>
            <div class="table-wrap heatmap-wrap">
              <table class="heatmap">
                <caption class="visually-hidden">
                  {{ metricLabel }} nach Wochentag und Stunde, Median je Zelle
                </caption>
                <thead>
                  <tr>
                    <th scope="col"><span class="visually-hidden">Wochentag</span></th>
                    <th v-for="hour in HOURS" :key="hour" scope="col" class="hour">{{ hour }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="weekday in WEEKDAYS" :key="weekday">
                    <th scope="row" class="day">{{ WEEKDAY_SHORT[weekday] }}</th>
                    <td v-for="hour in HOURS" :key="hour">
                      <span
                        class="cell"
                        role="img"
                        :aria-label="cellLabel(weekday, hour)"
                        :title="cellLabel(weekday, hour)"
                        :style="{ background: cellFill(weekday, hour) }"
                      ></span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <ul class="scale" aria-label="Farbskala">
              <li><span class="cell" :style="{ background: heatBackground(0) }"></span> keine Beiträge</li>
              <li><span class="cell" :style="{ background: heatBackground(1) }"></span> wenig</li>
              <li><span class="cell" :style="{ background: heatBackground(4) }"></span> viel</li>
            </ul>
          </section>

          <section class="section">
            <h3 class="section-title">Median je Veröffentlichungsstunde</h3>
            <div class="card chart-card">
              <BarChart
                v-if="hourChart.hasRows"
                :categories="hourChart.categories"
                :series-names="[metricLabel]"
                :label="`${metricLabel} nach Veröffentlichungsstunde`"
                :format="(value) => formatMetricValue(metricId, value)"
              />
              <p v-else class="explainer">Keine gemessenen Beiträge für diese Auswahl.</p>
            </div>
          </section>
        </template>
      </LoadState>
    </template>
  </div>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
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

.note,
.kpi-note,
.explainer {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--app-muted);
}

.heatmap-wrap {
  overflow-x: auto;
}

.heatmap {
  border-collapse: separate;
  border-spacing: 3px;
  font-size: 13px;
}

.heatmap .hour,
.heatmap .day {
  font-weight: 400;
  color: var(--app-muted);
  text-align: center;
  padding: 0 4px;
}

.heatmap .day {
  text-align: right;
}

.cell {
  display: inline-block;
  width: 26px;
  height: 26px;
  border-radius: 4px;
  background: var(--md-sys-color-surface-container);
  vertical-align: middle;
}

.scale {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  font-size: 15px;
  color: var(--app-muted);
}

.scale li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-card {
  padding: 16px 20px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
