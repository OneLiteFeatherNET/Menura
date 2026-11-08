<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../../api/client';
import type { Channel } from '../../api/types';
import LoadState from '../../components/LoadState.vue';
import { useAsync } from '../../composables/useAsync';
import { PLATFORM_LABELS } from '../../domain/format';
import { CHANNEL_PARAM, queryFromSelection, selectedFromQuery } from './channelQuery';

const ANALYTICS_TABS = [
  { to: '/analytics', label: 'Überblick' },
  { to: '/analytics/kanaele', label: 'Kanäle' },
  { to: '/analytics/uhrzeit', label: 'Uhrzeit & Wochentag' },
  { to: '/analytics/serien', label: 'Serien' },
  { to: '/analytics/join-attribution', label: 'Join-Attribution' },
  { to: '/analytics/datenpunkte', label: 'Datenpunkte' },
];

const route = useRoute();
const router = useRouter();
const { data: channels, error, loading, run } = useAsync<Channel[]>(() => api.channels());
onMounted(run);

const allIds = computed(() => (channels.value ?? []).map((channel) => channel.id));

// The selection lives in the query, so a shared link opens with the same channels.
const selected = computed(() => selectedFromQuery(route.query[CHANNEL_PARAM], allIds.value));

const pending = ref(false);

function isSelected(id: string): boolean {
  return selected.value.includes(id);
}

function toggle(id: string): void {
  const next = isSelected(id) ? selected.value.filter((other) => other !== id) : [...selected.value, id];
  pending.value = true;
  router
    .replace({ query: { ...route.query, [CHANNEL_PARAM]: queryFromSelection(next, allIds.value) } })
    .finally(() => {
      pending.value = false;
    });
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Analytics</h2>
        <p class="page-lead">Messwerte aller Kanäle an einer Stelle.</p>
      </div>
    </header>

    <section class="channel-select" aria-labelledby="channel-select-title">
      <h3 id="channel-select-title" class="channel-select-title">Kanäle</h3>
      <div class="chips" role="group" aria-labelledby="channel-select-title">
        <button
          v-for="channel in channels ?? []"
          :key="channel.id"
          type="button"
          class="chip"
          :class="{ 'is-selected': isSelected(channel.id) }"
          :aria-pressed="isSelected(channel.id) ? 'true' : 'false'"
          :disabled="pending"
          @click="toggle(channel.id)"
        >
          {{ PLATFORM_LABELS[channel.platform] }}
        </button>
      </div>
    </section>

    <nav class="tabs" aria-label="Analytics-Ansichten">
      <RouterLink
        v-for="tab in ANALYTICS_TABS"
        :key="tab.to"
        :to="tab.to"
        class="tab"
        exact-active-class="is-active"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>

    <LoadState :loading="loading" :error="error">
      <RouterView :channel-ids="selected" />
    </LoadState>
  </div>
</template>

<style scoped>
.channel-select {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  margin-bottom: 16px;
}

.channel-select-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--app-muted);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  box-sizing: border-box;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  background: transparent;
  color: var(--md-sys-color-on-surface);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.chip.is-selected {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  border-color: transparent;
}

.chip:disabled {
  cursor: progress;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.tab {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  box-sizing: border-box;
  padding: 0 16px;
  border-radius: 20px;
  border: 1px solid var(--md-sys-color-outline-variant);
  font-size: 15px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  text-decoration: none;
}

.tab:hover {
  background: color-mix(in srgb, var(--md-sys-color-on-surface) 8%, transparent);
}

.tab.is-active {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  border-color: transparent;
  font-weight: 500;
}
</style>
