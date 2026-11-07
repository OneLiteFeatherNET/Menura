<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { api } from '../api/client';
import LoadState from '../components/LoadState.vue';
import { useAsync } from '../composables/useAsync';
import { latestWeekOf, SOURCE_ORDER, summaryOf } from '../domain/attribution';
import { formatCount, PLATFORM_LABELS } from '../domain/format';

const WARNING_TITLES: Record<string, string> = {
  coverage: 'Serienabdeckung',
  playlist: 'Playlist',
  'no-player-series': 'Zielgruppe',
};

const { data, error, loading, run } = useAsync(async () => {
  const [weekly, coverage, comparison, warnings, channels] = await Promise.all([
    api.weeklyAttribution(),
    api.coverage(),
    api.seriesComparison(),
    api.warnings(),
    api.channels(),
  ]);
  return { weekly, coverage, comparison, warnings, channels };
});

onMounted(run);

const week = computed(() => (data.value ? latestWeekOf(data.value.weekly) : null));

const summary = computed(() =>
  data.value && week.value ? summaryOf(data.value.weekly, week.value) : null,
);

const newPlayers = computed(() => summary.value?.firstJoins ?? 0);

const weekCoverage = computed(() =>
  data.value && week.value ? data.value.coverage.filter((row) => row.week === week.value) : [],
);

const topSeries = computed(() => data.value?.comparison[0] ?? null);

function channelName(channelId: string): string {
  return data.value?.channels.find((channel) => channel.id === channelId)?.name ?? channelId;
}

function warningTitle(kind: string): string {
  return WARNING_TITLES[kind] ?? 'Hinweis';
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Übersicht</h2>
        <p class="page-lead">Stand {{ week ?? '–' }} · Demo-Daten</p>
      </div>
    </header>

    <LoadState :loading="loading" :error="error">
      <section class="section">
        <h3 class="section-title">Hinweise</h3>
        <p v-if="data && data.warnings.length === 0" class="muted">Keine offenen Hinweise.</p>
        <div class="warning-grid">
          <article v-for="(warning, index) in data?.warnings" :key="index" class="card warning-card">
            <span class="chip">{{ warningTitle(warning.kind) }}</span>
            <p class="warning-text">{{ warning.message }}</p>
          </article>
        </div>
      </section>

      <section class="section">
        <h3 class="section-title">Kennzahlen dieser Woche</h3>
        <div class="kpi-grid">
          <article class="card kpi kpi-wide">
            <p class="kpi-label">Neue Spieler · {{ week ?? '–' }}</p>
            <p class="kpi-value">{{ formatCount(newPlayers) }}</p>
            <p class="muted">Erstjoins gesamt · Quelle laut Spielern</p>
            <ul class="split">
              <li v-for="source in SOURCE_ORDER" :key="source">
                <span>{{ PLATFORM_LABELS[source] }}</span>
                <strong>{{ formatCount(summary?.bySource[source] ?? 0) }}</strong>
              </li>
            </ul>
          </article>

          <article
            v-for="row in weekCoverage"
            :key="row.channelId"
            class="card kpi"
            :class="{ 'kpi-flagged': row.flagged }"
          >
            <p class="kpi-label">Serienabdeckung · {{ channelName(row.channelId) }}</p>
            <p class="kpi-value">{{ Math.round(row.share * 100) }} %</p>
            <p class="muted">{{ row.covered }} von {{ row.shorts }} Shorts in einer Serie</p>
            <span v-if="row.flagged" class="chip">unter 90 %</span>
          </article>

          <article class="card kpi">
            <p class="kpi-label">Top-Serie · Engaged Views</p>
            <p class="kpi-value kpi-value-small">{{ topSeries?.name ?? '–' }}</p>
            <p class="muted">
              <template v-if="topSeries">{{ formatCount(topSeries.engagedViews) }} Engaged Views</template>
              <template v-else>Keine Daten</template>
            </p>
          </article>
        </div>
      </section>
    </LoadState>
  </div>
</template>

<style scoped>
.warning-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.warning-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.warning-text {
  margin: 0;
  font-size: 17px;
  line-height: 26px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kpi-wide {
  grid-column: span 2;
}

.kpi-label {
  margin: 0;
  font-size: 16px;
  color: var(--app-muted);
}

.kpi-value {
  margin: 0;
  font-size: 56px;
  line-height: 64px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}

.kpi-value-small {
  font-size: 28px;
  line-height: 36px;
}

.kpi-flagged {
  border-color: var(--md-sys-color-error);
}

.split {
  display: flex;
  gap: 24px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  flex-wrap: wrap;
}

.split li {
  display: flex;
  flex-direction: column;
}

.split strong {
  font-size: 24px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .kpi-wide {
    grid-column: auto;
  }
}
</style>
