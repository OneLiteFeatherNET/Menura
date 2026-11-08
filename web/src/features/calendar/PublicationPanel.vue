<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Channel, Series } from '../../api/types';
import { safeHttpsUrl } from '../../domain/url';
import { formatCount, NOT_AVAILABLE } from '../../domain/format';
import { berlinTime, formatBerlinDateTime, toDateInput, zonedToDate } from './dates';
import { calendarApi } from './api';
import { changeErrorMessage, platformLabel, statusLabel, STEP_LABELS, STEP_STATE_LABELS } from './labels';
import type { Capability, Publication, PublicationMetadata, Snapshot } from './types';

const props = defineProps<{
  publication: Publication;
  series: Series | null;
  channel: Channel | undefined;
  capability: Capability | undefined;
  canEdit: boolean;
}>();

const emit = defineEmits<{
  close: [];
  changed: [];
  deleted: [id: string];
}>();

const MODE_LABELS = { native: 'Plattform plant selbst', hub: 'Über Menura (Hub)' } as const;

const metrics = [
  { key: 'views', label: 'Aufrufe' },
  { key: 'engagedViews', label: 'Engaged Views' },
  { key: 'likes', label: 'Likes' },
  { key: 'comments', label: 'Kommentare' },
  { key: 'shares', label: 'Shares' },
] as const;

const time = computed(() => formatBerlinDateTime(new Date(props.publication.publishAt)));
const playlist = computed(() => safeHttpsUrl(props.publication.metadata.playlist) ?? null);
const editable = computed(() => props.canEdit && props.publication.status === 'planned');
const deletable = computed(() =>
  props.canEdit && (props.publication.status === 'planned' || props.publication.status === 'scheduled'),
);

// Metric snapshots of a published post. A request for an older one is dropped when a newer one has started.
const snapshots = ref<Snapshot[]>([]);
const snapshotsLoading = ref(false);
const snapshotsError = ref<string | null>(null);
let latestSnapshotRequest = 0;

watch(
  () => props.publication.postId,
  async (postId) => {
    const request = ++latestSnapshotRequest;
    snapshots.value = [];
    snapshotsError.value = null;
    if (!postId) {
      return;
    }
    snapshotsLoading.value = true;
    try {
      const loaded = await calendarApi.snapshots(postId);
      if (request === latestSnapshotRequest) {
        snapshots.value = loaded;
      }
    } catch (cause) {
      if (request === latestSnapshotRequest) {
        snapshotsError.value = changeErrorMessage(cause, 'change');
      }
    } finally {
      if (request === latestSnapshotRequest) {
        snapshotsLoading.value = false;
      }
    }
  },
  { immediate: true },
);

function metricText(value: number | null): string {
  return value === null ? NOT_AVAILABLE : formatCount(value);
}

// Editing: the time in Berlin and the metadata as text.
const confirming = ref(false);
const editDate = ref('');
const editTime = ref('');
const editTitle = ref('');
const editDescription = ref('');
const editTags = ref('');
const editPlaylist = ref('');
const saving = ref(false);
const saveError = ref<string | null>(null);

watch(
  () => props.publication,
  (value) => {
    const at = new Date(value.publishAt);
    editDate.value = toDateInput(at);
    editTime.value = berlinTime(at);
    editTitle.value = value.metadata.title;
    editDescription.value = value.metadata.description;
    editTags.value = value.metadata.tags.join(' ');
    editPlaylist.value = value.metadata.playlist ?? '';
    saveError.value = null;
    confirming.value = false;
  },
  { immediate: true },
);

function parsedTags(): string[] {
  return editTags.value.split(/[\s,]+/).filter((tag) => tag !== '');
}

async function save(): Promise<void> {
  saving.value = true;
  saveError.value = null;
  const metadata: PublicationMetadata = {
    title: editTitle.value.trim(),
    description: editDescription.value.trim(),
    tags: parsedTags(),
    playlist: editPlaylist.value.trim() === '' ? null : editPlaylist.value.trim(),
  };
  try {
    await calendarApi.updatePublication(props.publication.id, {
      publishAt: zonedToDate(editDate.value, editTime.value).toISOString(),
      metadata,
    });
    emit('changed');
  } catch (cause) {
    saveError.value = changeErrorMessage(cause, 'change');
  } finally {
    saving.value = false;
  }
}

// Deleting asks inline; a browser dialog is never used.
const deleting = ref(false);
const deleteError = ref<string | null>(null);

async function remove(): Promise<void> {
  deleting.value = true;
  deleteError.value = null;
  try {
    await calendarApi.deletePublication(props.publication.id);
    emit('deleted', props.publication.id);
  } catch (cause) {
    deleteError.value = changeErrorMessage(cause, 'delete');
  } finally {
    deleting.value = false;
  }
}
</script>

<template>
  <aside class="panel" aria-label="Veröffentlichung">
    <header class="panel-head">
      <div class="panel-title">
        <span class="status" :class="`status-${publication.status}`">{{ statusLabel(publication.status) }}</span>
        <h2>{{ publication.metadata.title }}</h2>
      </div>
      <md-text-button type="button" @click="emit('close')">Schließen</md-text-button>
    </header>

    <dl class="facts">
      <dt>Kanal</dt>
      <dd>{{ channel?.name ?? publication.channelId }} · {{ platformLabel(publication.platform) }}</dd>
      <dt>Serie</dt>
      <dd>{{ series?.name ?? 'ohne Serie' }}</dd>
      <dt>Termin</dt>
      <dd>{{ time }} Uhr</dd>
      <dt>Modus</dt>
      <dd>
        {{ MODE_LABELS[publication.mode] }}
        <span v-if="capability" class="note">{{ capability.note }}</span>
      </dd>
    </dl>

    <section class="block">
      <h3>Metadaten</h3>
      <p class="prose">{{ publication.metadata.description || '–' }}</p>
      <p class="tags">{{ publication.metadata.tags.join(' ') || 'keine Tags' }}</p>
      <p v-if="playlist">
        Playlist: <a :href="playlist" target="_blank" rel="noopener noreferrer">{{ playlist }}</a>
      </p>
      <p v-else-if="publication.metadata.playlist">Playlist: {{ publication.metadata.playlist }}</p>
      <p v-else class="muted">keine Playlist</p>
    </section>

    <section class="block">
      <h3>Schritte</h3>
      <ol class="steps">
        <li v-for="step in publication.steps" :key="step.name" :data-step="step.name" :class="`state-${step.state}`">
          <span class="step-name">{{ STEP_LABELS[step.name] }}</span>
          <span class="step-state">{{ STEP_STATE_LABELS[step.state] }}</span>
          <span v-if="step.at" class="step-at">{{ formatBerlinDateTime(new Date(step.at)) }}</span>
          <span v-if="step.detail" class="step-detail">{{ step.detail }}</span>
        </li>
      </ol>
    </section>

    <section v-if="publication.postId" class="block">
      <h3>Messwerte</h3>
      <p v-if="snapshotsLoading" class="muted" role="status">Messwerte werden geladen …</p>
      <p v-else-if="snapshotsError" class="error" role="alert">{{ snapshotsError }}</p>
      <p v-else-if="snapshots.length === 0" class="muted">Noch keine Messwerte</p>
      <div v-else class="table-wrap">
        <table class="data-table snapshots">
          <thead>
            <tr>
              <th>Zeitpunkt</th>
              <th v-for="metric in metrics" :key="metric.key" class="num">{{ metric.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="snapshot in snapshots" :key="snapshot.offset">
              <td>{{ snapshot.offset }}</td>
              <td v-for="metric in metrics" :key="metric.key" class="num" :data-metric="metric.key">
                {{ metricText(snapshot[metric.key]) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="editable" class="block">
      <h3>Bearbeiten</h3>
      <form class="edit" @submit.prevent="save">
        <label>Datum <input v-model="editDate" type="date" name="date" required /></label>
        <label>Uhrzeit (Berlin) <input v-model="editTime" type="time" name="time" required /></label>
        <label>Titel <input v-model="editTitle" type="text" name="title" maxlength="120" required /></label>
        <label>Beschreibung <textarea v-model="editDescription" name="description" rows="4" maxlength="5000"></textarea></label>
        <label>Tags (Hashtags, durch Leerzeichen getrennt) <input v-model="editTags" type="text" name="tags" /></label>
        <label>Playlist (https-URL) <input v-model="editPlaylist" type="url" name="playlist" /></label>
        <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
        <md-filled-button type="submit" :disabled="saving">Termin speichern</md-filled-button>
      </form>
    </section>

    <section v-if="deletable" class="block">
      <div v-if="!confirming">
        <md-text-button type="button" class="danger" @click="confirming = true">Löschen</md-text-button>
      </div>
      <div v-else class="confirm" role="group" aria-label="Löschen bestätigen">
        <p>Veröffentlichung wirklich löschen?</p>
        <div class="confirm-actions">
          <md-filled-button type="button" class="danger-fill" :disabled="deleting" @click="remove">Ja, löschen</md-filled-button>
          <md-text-button type="button" @click="confirming = false">Abbrechen</md-text-button>
        </div>
      </div>
      <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
    </section>
  </aside>
</template>

<style scoped>
.panel {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-card);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.panel-title {
  display: grid;
  gap: 6px;
}

.panel-title h2 {
  margin: 0;
  font-size: 20px;
  line-height: 26px;
}

.status {
  justify-self: start;
  padding: 2px 10px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  font-size: 13px;
  font-weight: 600;
}

.status-failed {
  color: var(--md-sys-color-error);
  border-color: var(--md-sys-color-error);
}

.status-published {
  color: var(--md-sys-color-primary);
  border-color: var(--md-sys-color-primary);
}

.facts {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 6px 16px;
  margin: 0;
  font-size: 14px;
}

.facts dt {
  color: var(--app-muted);
}

.facts dd {
  margin: 0;
}

.note {
  display: block;
  color: var(--app-muted);
}

.block h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.prose,
.tags {
  margin: 0 0 6px;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.steps {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
  font-size: 14px;
}

.steps li {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.step-name {
  font-weight: 600;
}

.step-detail {
  flex-basis: 100%;
  color: var(--md-sys-color-error);
}

.state-failed .step-state {
  color: var(--md-sys-color-error);
}

.state-done .step-state {
  color: var(--md-sys-color-primary);
}

.snapshots {
  font-size: 13px;
}

.edit {
  display: grid;
  gap: 10px;
}

.edit label {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: var(--app-muted);
}

.edit input,
.edit textarea {
  font: inherit;
  padding: 8px 10px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 8px;
  background: var(--app-card);
  color: inherit;
}

.error {
  color: var(--md-sys-color-error);
  margin: 0;
}

.confirm {
  display: grid;
  gap: 8px;
}

.confirm p {
  margin: 0;
}

.confirm-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.danger {
  --md-text-button-label-text-color: var(--md-sys-color-error);
}

.danger-fill {
  --md-filled-button-container-color: var(--md-sys-color-error);
  --md-filled-button-label-text-color: var(--md-sys-color-on-error);
}
</style>
