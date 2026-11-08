<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Channel, Series } from '../../api/types';
import { calendarApi } from './api';
import { addDays, berlinDateKey, toDateInput, zonedToDate } from './dates';
import { useNow } from './now';
import { platformLabel, publicationErrorMessage } from './labels';
import type { Capability, ClipSummary, Publication } from './types';

const props = defineProps<{
  open: boolean;
  clips: ClipSummary[];
  series: Series[];
  channels: Channel[];
  platforms: Record<string, Capability>;
  preselectAssetId: string | null;
}>();

const emit = defineEmits<{
  close: [];
  planned: [publications: Publication[]];
}>();

const now = useNow();

const assetId = ref('');
const channelIds = ref<string[]>([]);
const date = ref('');
const time = ref('');
const submitting = ref(false);
const error = ref<string | null>(null);

const selectedClip = computed(() => props.clips.find((clip) => clip.id === assetId.value) ?? null);
const selectedSeries = computed(() => {
  const seriesId = selectedClip.value?.seriesId;
  return props.series.find((candidate) => candidate.id === seriesId) ?? null;
});

/** The defaults: the preselected or first clip, the next day, the series slot of the clip (18:00 otherwise). */
function reset(): void {
  assetId.value = props.preselectAssetId ?? props.clips[0]?.id ?? '';
  channelIds.value = [];
  date.value = addDays(berlinDateKey(now()), 1);
  time.value = '18:00';
  error.value = null;
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      reset();
    }
  },
  { immediate: true },
);

// The clips load after the dialog opens: the preselected clip (or the first one) is chosen once they arrive.
watch(
  () => props.clips,
  (clips) => {
    if (props.open && !clips.some((clip) => clip.id === assetId.value)) {
      assetId.value = props.preselectAssetId ?? clips[0]?.id ?? '';
    }
  },
);

watch(selectedSeries, (series) => {
  if (series) {
    time.value = series.slot;
  }
});

/** The metadata the plan will carry, as far as the client knows it: title, tags, and the playlist per channel. */
const preview = computed(() => {
  const clip = selectedClip.value;
  if (!clip) {
    return null;
  }
  const tags = [selectedSeries.value?.hashtag, ...clip.tags.map((tag) => `#${tag}`)]
    .filter((tag): tag is string => Boolean(tag));
  return {
    title: clip.title,
    tags: [...new Set(tags)].slice(0, 15).join(' '),
  };
});

const chosenChannels = computed(() => props.channels.filter((channel) => channelIds.value.includes(channel.id)));

function playlistOf(channel: Channel): string {
  return selectedSeries.value?.playlists[channel.id] ?? 'keine Playlist';
}

function noteOf(channel: Channel): string {
  const capability = props.platforms[channel.platform];
  const mode = capability?.nativeSchedule ? 'Plattform plant selbst' : 'Über Menura (Hub)';
  return `${platformLabel(channel.platform)}: ${mode}. ${capability?.note ?? ''}`.trim();
}

async function submit(): Promise<void> {
  error.value = null;
  if (!selectedClip.value) {
    error.value = 'Bitte einen Clip wählen.';
    return;
  }
  if (channelIds.value.length === 0) {
    error.value = 'Bitte mindestens einen Kanal wählen.';
    return;
  }
  if (date.value === '' || time.value === '') {
    error.value = 'Bitte Datum und Uhrzeit angeben.';
    return;
  }
  submitting.value = true;
  try {
    const created = await calendarApi.createPublications({
      assetId: selectedClip.value.id,
      channelIds: [...channelIds.value],
      publishAt: zonedToDate(date.value, time.value).toISOString(),
    });
    emit('planned', created);
  } catch (cause) {
    error.value = publicationErrorMessage(cause);
  } finally {
    submitting.value = false;
  }
}

/** Today in Berlin, as the minimum of the date input (informational; the server enforces the lead time). */
const minDate = computed(() => toDateInput(now()));
const slotHint = computed(() => (selectedSeries.value ? `Serienslot ${selectedSeries.value.slot} Uhr` : ''));
</script>

<template>
  <div v-if="open" class="scrim">
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="plan-title">
      <h2 id="plan-title">Clip einplanen</h2>

      <form class="plan-form" @submit.prevent="submit">
        <label class="field">
          <span>Clip</span>
          <select v-model="assetId" name="clip" required>
            <option v-for="clip in clips" :key="clip.id" :value="clip.id">{{ clip.title }}</option>
          </select>
        </label>
        <p v-if="clips.length === 0" class="muted">Keine fertig gerenderten Clips vorhanden.</p>

        <fieldset class="field channels">
          <legend>Kanäle</legend>
          <label v-for="channel in channels" :key="channel.id" class="check">
            <input v-model="channelIds" type="checkbox" name="channel" :value="channel.id" />
            {{ channel.name }}
          </label>
        </fieldset>

        <div class="when">
          <label class="field">
            <span>Datum</span>
            <input v-model="date" type="date" name="date" :min="minDate" required />
          </label>
          <label class="field">
            <span>Uhrzeit (Berlin)</span>
            <input v-model="time" type="time" name="time" required />
          </label>
          <p v-if="slotHint" class="muted hint">{{ slotHint }}</p>
        </div>

        <section v-if="preview" class="preview" aria-label="Vorschau der Metadaten">
          <p><strong>Titel:</strong> {{ preview.title }}</p>
          <p><strong>Tags:</strong> {{ preview.tags || 'keine' }}</p>
          <ul v-if="chosenChannels.length > 0" class="notes">
            <li v-for="channel in chosenChannels" :key="channel.id">
              <span>{{ channel.name }} – Playlist: {{ playlistOf(channel) }}</span>
              <span class="note">{{ noteOf(channel) }}</span>
            </li>
          </ul>
          <p v-else class="muted">Kanäle wählen, um die Plattformhinweise zu sehen.</p>
          <p class="muted">Ausgabe um {{ time || '–' }} Uhr (Berlin), ab 15 Minuten in der Zukunft.</p>
        </section>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <div class="actions">
          <md-text-button type="button" @click="$emit('close')">Abbrechen</md-text-button>
          <md-filled-button type="submit" :disabled="submitting">Einplanen</md-filled-button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.scrim {
  position: fixed;
  inset: 0;
  z-index: 10;
  display: grid;
  place-items: center;
  padding: 16px;
  background: color-mix(in srgb, black 40%, transparent);
}

.dialog {
  width: min(560px, 100%);
  max-height: 90vh;
  overflow: auto;
  padding: 24px;
  border-radius: 24px;
  background: var(--app-card);
  color: inherit;
}

.dialog h2 {
  margin: 0 0 12px;
  font-size: 22px;
}

.plan-form {
  display: grid;
  gap: 14px;
}

.field {
  display: grid;
  gap: 4px;
  border: 0;
  padding: 0;
  font-size: 14px;
}

.field span,
.field legend {
  color: var(--app-muted);
}

.field select,
.field input[type='date'],
.field input[type='time'] {
  font: inherit;
  padding: 8px 10px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 8px;
  background: var(--app-card);
  color: inherit;
}

.channels {
  display: grid;
  gap: 6px;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.when {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
}

.hint {
  margin: 0;
  font-size: 13px;
}

.preview {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  font-size: 14px;
}

.preview p,
.notes {
  margin: 0;
}

.notes {
  display: grid;
  gap: 6px;
  padding-left: 18px;
}

.note {
  display: block;
  color: var(--app-muted);
  font-size: 13px;
}

.error {
  margin: 0;
  color: var(--md-sys-color-error);
}

.muted {
  color: var(--app-muted);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
