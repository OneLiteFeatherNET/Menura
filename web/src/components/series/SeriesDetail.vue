<script setup lang="ts">
import { computed } from 'vue';
import DataTable, { type DataColumn } from '../ui/DataTable.vue';
import LoadState from '../ui/LoadState.vue';
import MetaList from '../ui/MetaList.vue';
import {
  AUDIENCE_LABELS,
  WEEKDAY_LABELS,
  type Episode,
  type SeriesDetail,
} from '../../api/series';

// One series with its brand values and its episodes. Shows only; the back action is reported as an event.
const props = withDefaults(
  defineProps<{
    status: 'loading' | 'error' | 'empty' | 'ready';
    detail: SeriesDetail | null;
    errorMessage?: string;
  }>(),
  { errorMessage: 'Die Serie konnte nicht geladen werden.' },
);

const emit = defineEmits<{ retry: []; back: [] }>();

const episodeColumns: DataColumn[] = [
  { key: 'number', label: 'Folge' },
  { key: 'title', label: 'Titel' },
  { key: 'publishedAt', label: 'Veröffentlicht' },
  { key: 'playlist', label: 'Playlist' },
];

const PLAYLIST_LABELS: Record<string, string> = {
  in_playlist: 'In der Playlist',
  missing: 'Fehlt in der Playlist',
  not_checked: 'Nicht geprüft',
};

function playlistLabel(state: string | null): string {
  return state ? (PLAYLIST_LABELS[state] ?? state) : '';
}

function dateLabel(iso: string): string {
  return new Date(iso).toLocaleDateString('de-DE');
}

function episodeRow(episode: Episode): Record<string, unknown> {
  return {
    number: episode.number,
    title: episode.title,
    publishedAt: dateLabel(episode.publishedAt),
    playlist: playlistLabel(episode.playlistState),
  };
}

const episodeRows = computed(() => (props.detail?.episodes ?? []).map(episodeRow));

const meta = computed(() => {
  const series = props.detail?.series;
  if (!series) {
    return [];
  }
  const badge = Object.entries(series.badgeText)
    .map(([language, text]) => `${text} (${language})`)
    .join(', ');
  return [
    { label: 'Hashtag', value: series.hashtag },
    { label: 'Eröffnung', value: series.openingFormula },
    { label: 'Badge', value: badge },
    { label: 'Farbe (Marken-Token)', value: series.colourToken },
    { label: 'Badge-Text (Marken-Token)', value: series.badgeToken },
    { label: 'Layout-Vorlage', value: series.layoutTemplate },
    { label: 'Zielgruppe', value: AUDIENCE_LABELS[series.audience] },
    { label: 'Bevorzugter Termin', value: `${WEEKDAY_LABELS[series.weekday - 1] ?? ''}, ${series.postingSlot} Uhr` },
    { label: 'Status', value: series.active ? 'aktiv' : 'inaktiv' },
  ];
});
</script>

<template>
  <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
    <section v-if="detail" class="series-detail" aria-label="Serie">
      <button type="button" class="series-detail__back" @click="emit('back')">Zurück zur Übersicht</button>
      <h2 class="series-detail__title">{{ detail.series.name }}</h2>
      <MetaList :items="meta" />
      <DataTable
        caption="Folgen der Serie"
        :columns="episodeColumns"
        :rows="episodeRows"
        row-key="number"
        empty-text="Noch keine Folge zugeordnet."
      />
    </section>
  </LoadState>
</template>

<style scoped>
.series-detail {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.series-detail__back {
  justify-self: start;
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.series-detail__title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
</style>
