<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/client';
import type { Channel, Series } from '../api/types';
import ShortPreview from '../components/ShortPreview.vue';
import LoadState from '../components/LoadState.vue';
import { useAsync } from '../composables/useAsync';
import { swatchColors } from '../domain/color';
import { isSlotVisible, SHORT_DURATION_SECONDS, SLOT_LABELS, slotWindowLabel } from '../domain/slots';

// Choice values of the selects: "" is the default (first entry), NONE is an explicit "none".
const NONE = 'none';

const router = useRouter();

const { data, error, loading, run } = useAsync(async () => {
  const [kit, series, channels] = await Promise.all([
    api.brandKit(),
    api.series(),
    api.channels(),
  ]);
  return { kit, series, channels };
});

onMounted(run);

const second = ref(0);
const selectedSeries = ref('');
const selectedChannel = ref('');

const template = computed(() => {
  const kit = data.value?.kit;
  return kit?.templates.find((candidate) => candidate.id === 'short-9x16') ?? kit?.templates[0] ?? null;
});

const activeSeries = computed<Series | null>(() => {
  const list = data.value?.series ?? [];
  if (selectedSeries.value === NONE) {
    return null;
  }
  return list.find((item) => item.id === selectedSeries.value) ?? list[0] ?? null;
});

const activeChannel = computed<Channel | null>(() => {
  const list = data.value?.channels ?? [];
  return list.find((item) => item.id === selectedChannel.value) ?? list[0] ?? null;
});

const visibleNow = computed(() =>
  (template.value?.slots ?? [])
    .filter((slot) => isSlotVisible(slot, second.value))
    .map((slot) => SLOT_LABELS[slot.name] ?? slot.name),
);

function valueOf(event: Event): string {
  return (event.target as HTMLInputElement).value;
}

function tokenStyle(background: string): Record<string, string> {
  return swatchColors(background);
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Brand-Kit</h2>
        <p class="page-lead">Farben, Schutzbereiche und das 9:16-Short. Quelle: Overlay-Theme.</p>
      </div>
      <md-outlined-button @click="router.push('/brand-kit/render-bundle')">Render-Bundle anzeigen</md-outlined-button>
    </header>

    <LoadState :loading="loading" :error="error">
      <template v-if="data">
        <section class="section">
          <h3 class="section-title">Farb-Tokens</h3>
          <div class="token-grid">
            <div class="token-head">Token</div>
            <div class="token-head">Hell</div>
            <div class="token-head">Dunkel</div>
            <template v-for="token in data.kit.tokens" :key="token.name">
              <div class="token-name">{{ token.name }}</div>
              <div class="swatch" :style="tokenStyle(token.light)">{{ token.light }}</div>
              <div class="swatch" :style="tokenStyle(token.dark)">{{ token.dark }}</div>
            </template>
          </div>
          <p class="muted">
            Marke:
            <template v-for="mark in data.kit.marks" :key="mark.name">
              {{ mark.name }} (Schutzbereich {{ mark.safeAreaPercent }} %)
            </template>
          </p>
        </section>

        <section v-if="template" class="section">
          <h3 class="section-title">Short 9:16 · {{ template.width }} × {{ template.height }}</h3>
          <div class="preview-layout">
            <ShortPreview
              :template="template"
              :second="second"
              :series="activeSeries"
              :channel="activeChannel"
            />

            <div class="controls">
              <md-outlined-select
                label="Serie (Badge-Farbe)"
                :value="selectedSeries"
                @change="selectedSeries = valueOf($event)"
              >
                <md-select-option
                  v-for="item in data.series"
                  :key="item.id"
                  :value="item.id"
                  :headline="item.name"
                ></md-select-option>
                <md-select-option :value="NONE" headline="ohne Serie (Standardfarbe)"></md-select-option>
              </md-outlined-select>

              <md-outlined-select
                label="Kanal (Join-Host)"
                :value="selectedChannel"
                @change="selectedChannel = valueOf($event)"
              >
                <md-select-option
                  v-for="item in data.channels"
                  :key="item.id"
                  :value="item.id"
                  :headline="item.name"
                ></md-select-option>
              </md-outlined-select>

              <div class="timeline">
                <div class="timeline-head">
                  <span class="muted">Zeitpunkt im Short</span>
                  <strong class="timeline-value">{{ second }} s von {{ SHORT_DURATION_SECONDS }} s</strong>
                </div>
                <md-slider
                  min="0"
                  :max="SHORT_DURATION_SECONDS"
                  step="1"
                  :value="second"
                  labeled
                  aria-label="Zeitpunkt im Short"
                  @input="second = Number(valueOf($event))"
                ></md-slider>
              </div>

              <div>
                <p class="muted visible-caption">Sichtbar bei {{ second }} s</p>
                <p class="visible-now">
                  <span v-for="label in visibleNow" :key="label" class="chip chip-on">{{ label }}</span>
                </p>
              </div>

              <table class="data-table slot-table">
                <thead>
                  <tr>
                    <th>Element</th>
                    <th>Zeitfenster</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="slot in template.slots" :key="slot.name">
                    <td>{{ SLOT_LABELS[slot.name] ?? slot.name }}</td>
                    <td>{{ slotWindowLabel(slot) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </template>
    </LoadState>
  </div>
</template>

<style scoped>
.token-grid {
  display: grid;
  grid-template-columns: minmax(160px, 240px) minmax(160px, 1fr) minmax(160px, 1fr);
  gap: 8px;
  align-items: center;
  max-width: 760px;
}

.token-head {
  font-size: 14px;
  color: var(--app-muted);
}

.token-name {
  font-size: 15px;
}

.swatch {
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  border: 1px solid var(--app-border);
}

.preview-layout {
  display: grid;
  grid-template-columns: auto minmax(360px, 1fr);
  gap: 32px;
  align-items: start;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.timeline-value {
  font-size: 20px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

md-slider {
  width: 100%;
}

.visible-caption {
  margin: 0 0 8px;
  font-size: 14px;
}

.visible-now {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
}

.chip-on {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  border-color: transparent;
}

.slot-table {
  max-width: 480px;
}

@media (max-width: 1100px) {
  .preview-layout {
    grid-template-columns: 1fr;
  }
}
</style>
