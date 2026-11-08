<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../../api/client';
import type { Channel } from '../../api/types';
import LoadState from '../../components/LoadState.vue';
import { analyticsApi } from './api';
import { dayLabel, deltaPercent, comparisonWindows, formatDelta, formatMetricValue, totalOf, unavailableNote } from './format';
import LineChart from './charts/LineChart.vue';
import type { AggregateResult, AggregateRow } from './types';

const props = defineProps<{ channelIds: string[] }>();

// The tiles of the overview. Each is a catalogue metric; the titles are the ones the overview names.
const TILES = [
  { metric: 'views', title: 'Aufrufe' },
  { metric: 'engagedViews', title: 'Engaged Views' },
  { metric: 'likes', title: 'Likes' },
  { metric: 'shares', title: 'Shares' },
  { metric: 'avgViewPercent', title: 'Ø angesehen (%)' },
] as const;

/** Totals are read at +24 h: a post younger than that has no measurement yet and is not counted. */
const OFFSET = '+24h';

interface Tile {
  metric: string;
  title: string;
  state: 'value' | 'empty' | 'unavailable';
  current: number | null;
  previous: number | null;
  delta: number | null;
  note: string;
}

const channels = ref<Channel[]>([]);
const tiles = ref<Tile[]>([]);
const viewsByDay = ref<AggregateRow[]>([]);
const loadError = ref<string | null>(null);
const loading = ref(true);
let latest = 0;

function platformsOf(missing: string[]): string[] {
  return [...new Set(missing.map((id) => channels.value.find((c) => c.id === id)?.platform ?? id))];
}

function buildTile(
  metric: string,
  title: string,
  current: AggregateResult,
  previous: AggregateResult,
  selected: string[],
): Tile {
  const platforms = platformsOf(current.missing);
  const note = platforms.length > 0 ? unavailableNote(platforms) : '';
  if (current.missing.length >= selected.length && selected.length > 0) {
    return { metric, title, state: 'unavailable', current: null, previous: null, delta: null, note };
  }
  const now = totalOf(metric, current.rows);
  const before = totalOf(metric, previous.rows);
  return {
    metric,
    title,
    state: now === null ? 'empty' : 'value',
    current: now,
    previous: before,
    delta: deltaPercent(now, before),
    note,
  };
}

function aggregateOver(
  metric: string,
  channelIds: string[],
  window: { from: string; to: string },
): Promise<AggregateResult> {
  return analyticsApi.aggregate({ metric, channelIds, groupBy: 'day', offset: OFFSET, from: window.from, to: window.to });
}

async function load(): Promise<void> {
  const request = ++latest;
  loading.value = true;
  loadError.value = null;
  const selected = [...props.channelIds];
  try {
    channels.value = await api.channels();
    if (selected.length === 0) {
      tiles.value = [];
      viewsByDay.value = [];
      return;
    }
    const windows = comparisonWindows(new Date());
    const pairs = await Promise.all(
      TILES.map(async (tile) => {
        const [current, previous] = await Promise.all([
          aggregateOver(tile.metric, selected, windows.current),
          aggregateOver(tile.metric, selected, windows.previous),
        ]);
        return { tile, current, previous };
      }),
    );
    if (request !== latest) {
      return;
    }
    tiles.value = pairs.map(({ tile, current, previous }) =>
      buildTile(tile.metric, tile.title, current, previous, selected),
    );
    viewsByDay.value = pairs.find(({ tile }) => tile.metric === 'views')?.current.rows ?? [];
  } catch (cause) {
    if (request === latest) {
      loadError.value = cause instanceof Error ? cause.message : 'Unerwarteter Fehler.';
    }
  } finally {
    if (request === latest) {
      loading.value = false;
    }
  }
}

onMounted(load);
watch(() => props.channelIds.join(','), load);

const dayPoints = computed(() =>
  viewsByDay.value.map((row) => ({ label: dayLabel(row.key), value: row.value })),
);

function tileValue(tile: Tile): string {
  if (tile.state === 'unavailable') {
    return 'nicht verfügbar';
  }
  if (tile.state === 'empty') {
    return 'keine Daten';
  }
  return formatMetricValue(tile.metric, tile.current);
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Überblick</h2>
        <p class="page-lead">
          Letzte 7 Tage gegenüber den 7 Tagen davor, gemessen nach +24 h. Jüngere Beiträge fließen erst danach ein.
        </p>
      </div>
    </header>

    <p v-if="channelIds.length === 0" class="card">Kein Kanal ausgewählt. Wähle oben mindestens einen Kanal.</p>

    <LoadState :loading="loading" :error="loadError">
      <template v-if="channelIds.length > 0">
        <div class="kpi-grid">
          <article v-for="tile in tiles" :key="tile.metric" class="kpi card" :aria-label="tile.title">
            <h3 class="kpi-label">{{ tile.title }}</h3>
            <p class="kpi-value" :class="{ 'is-muted': tile.state !== 'value' }">{{ tileValue(tile) }}</p>
            <p v-if="tile.state === 'value'" class="kpi-delta">
              {{ formatDelta(tile.delta) }}
              <span class="kpi-previous">gegenüber {{ formatMetricValue(tile.metric, tile.previous) }}</span>
            </p>
            <p v-if="tile.note" class="kpi-note">{{ tile.note }}</p>
          </article>
        </div>

        <section class="section">
          <h3 class="section-title">Aufrufe pro Veröffentlichungstag</h3>
          <div class="card chart-card">
            <LineChart
              v-if="dayPoints.length > 0"
              :points="dayPoints"
              label="Aufrufe pro Veröffentlichungstag, letzte 7 Tage"
              :format="(value) => formatMetricValue('views', value)"
            />
            <p v-else class="explainer">Keine gemessenen Beiträge in den letzten 7 Tagen.</p>
          </div>
        </section>
      </template>
    </LoadState>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 20px;
}

.kpi-label {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--app-muted);
}

.kpi-value {
  margin: 0;
  font-size: 28px;
  font-weight: 500;
}

.kpi-value.is-muted {
  font-size: 18px;
  color: var(--app-muted);
}

.kpi-delta,
.kpi-note {
  margin: 0;
  font-size: 15px;
}

.kpi-previous,
.kpi-note {
  color: var(--app-muted);
}

.chart-card {
  padding: 16px 20px;
}

.explainer {
  margin: 0;
  font-size: 16px;
  color: var(--app-muted);
}
</style>
