<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchBrandKit } from '../../brand/brandKit';
import PageHeader from '../ui/PageHeader.vue';
import {
  createSeries,
  fetchSeriesDetail,
  fetchSeriesOverview,
  type SeriesDetail,
  type SeriesDraft,
  type SeriesOverview,
} from '../../api/series';
import SeriesDetailView from './SeriesDetail.vue';
import SeriesForm from './SeriesForm.vue';
import SeriesOverviewView from './SeriesOverview.vue';

// The series page: the overview, the creation form (editors only) and one series. It loads the data and reports the
// outcome of each action to the view components.
defineProps<{ canEdit: boolean }>();

type Mode = 'list' | 'create' | 'detail';
type Status = 'loading' | 'error' | 'empty' | 'ready';

const mode = ref<Mode>('list');
const status = ref<Status>('loading');
const overview = ref<SeriesOverview | null>(null);
const detail = ref<SeriesDetail | null>(null);
const selectedId = ref('');
const detailStatus = ref<Status>('loading');
const colourTokens = ref<string[]>([]);
const templates = ref<string[]>([]);
const formError = ref<string | undefined>(undefined);
const saving = ref(false);

async function loadOverview() {
  status.value = 'loading';
  try {
    // The overview is shown even without series: its warning is what tells the editor that a series is missing.
    overview.value = await fetchSeriesOverview();
    status.value = 'ready';
  } catch {
    status.value = 'error';
  }
}

async function loadBrandChoices() {
  try {
    const version = await fetchBrandKit();
    colourTokens.value = version.kit.colours.map((colour) => colour.name);
    templates.value = version.kit.templates.map((template) => template.format);
  } catch {
    colourTokens.value = [];
    templates.value = [];
  }
}

async function openSeries(id: string) {
  selectedId.value = id;
  mode.value = 'detail';
  detailStatus.value = 'loading';
  try {
    detail.value = await fetchSeriesDetail(id);
    detailStatus.value = 'ready';
  } catch {
    detailStatus.value = 'error';
  }
}

function startCreate() {
  formError.value = undefined;
  mode.value = 'create';
}

function backToList() {
  mode.value = 'list';
  loadOverview();
}

async function save(draft: SeriesDraft) {
  saving.value = true;
  formError.value = undefined;
  try {
    await createSeries(draft);
    mode.value = 'list';
    await loadOverview();
  } catch (failure) {
    formError.value = failure instanceof Error ? failure.message : 'Die Serie konnte nicht gespeichert werden.';
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadOverview();
  loadBrandChoices();
});
</script>

<template>
  <section class="series-page">
    <PageHeader title="Serien" subtitle="Wiedererkennbare Reihen aus den Shorts, ihre Zuordnung und Abdeckung." />

    <template v-if="mode === 'list'">
      <button v-if="canEdit" type="button" class="series-page__new" @click="startCreate">Neue Serie</button>
      <SeriesOverviewView
        :status="status"
        :overview="overview"
        error-message="Die Serien konnten nicht geladen werden."
        @retry="loadOverview"
        @open="openSeries"
      />
    </template>

    <template v-else-if="mode === 'create'">
      <button type="button" class="series-page__back" @click="backToList">Zurück zur Übersicht</button>
      <SeriesForm
        v-if="colourTokens.length > 0 && templates.length > 0"
        :colour-tokens="colourTokens"
        :templates="templates"
        :busy="saving"
        :error="formError"
        @submit="save"
      />
      <p v-else class="series-page__hint" role="status">
        Die Marke hat noch keine Farben oder Vorlagen. Eine Serie kann erst angelegt werden, wenn die Marke veröffentlicht ist.
      </p>
    </template>

    <SeriesDetailView
      v-else
      :status="detailStatus"
      :detail="detail"
      error-message="Die Serie konnte nicht geladen werden."
      @retry="openSeries(selectedId)"
      @back="backToList"
    />
  </section>
</template>

<style scoped>
.series-page {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.series-page__new,
.series-page__back {
  justify-self: start;
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.series-page__hint {
  margin: 0;
  font-size: 14px;
}
</style>
