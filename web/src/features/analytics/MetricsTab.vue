<script setup lang="ts">
import { onMounted, ref } from 'vue';
import LoadState from '../../components/LoadState.vue';
import { analyticsApi } from './api';
import type { Metric } from './types';

/** The platforms of the catalogue, in column order, with the API each one's numbers come from. */
const PLATFORMS = [
  { id: 'youtube', label: 'YouTube', source: 'YouTube Analytics API' },
  { id: 'tiktok', label: 'TikTok', source: 'TikTok Display API' },
  { id: 'instagram', label: 'Instagram', source: 'Instagram Graph API Insights' },
] as const;

const metrics = ref<Metric[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    metrics.value = await analyticsApi.metrics();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Unerwarteter Fehler.';
  } finally {
    loading.value = false;
  }
});

function isAvailable(metric: Metric, platform: string): boolean {
  return metric.platforms.includes(platform);
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Datenpunkte</h2>
        <p class="page-lead">
          Welche Kennzahl welche Plattform liefert. Fehlt eine Kennzahl, steht dort „nicht verfügbar“ und nie 0.
        </p>
      </div>
    </header>

    <LoadState :loading="loading" :error="error">
      <section class="section">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Kennzahl</th>
                <th v-for="platform in PLATFORMS" :key="platform.id" class="num">{{ platform.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="metric in metrics" :key="metric.id">
                <td>{{ metric.label }}</td>
                <td v-for="platform in PLATFORMS" :key="platform.id" class="num">
                  <span :class="isAvailable(metric, platform.id) ? 'is-available' : 'is-missing'">
                    {{ isAvailable(metric, platform.id) ? 'verfügbar' : 'nicht verfügbar' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="section">
        <h3 class="section-title">Quellen</h3>
        <ul class="sources">
          <li v-for="platform in PLATFORMS" :key="platform.id">
            <strong>{{ platform.label }}:</strong> {{ platform.source }}
          </li>
        </ul>
      </section>
    </LoadState>
  </div>
</template>

<style scoped>
.is-available {
  color: var(--md-sys-color-on-surface);
}

.is-missing {
  color: var(--app-muted);
}

.sources {
  margin: 0;
  padding-left: 20px;
  font-size: 16px;
  line-height: 1.7;
}
</style>
