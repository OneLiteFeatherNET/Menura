<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue';
import { errorMessage } from '../../api/client';
import { SESSION_KEY } from '../../auth/session';
import LoadState from '../../components/LoadState.vue';
import { useAsync } from '../../composables/useAsync';
import { poolApi } from './api';
import { formatCreatedAt, formatDuration, kindLabel, stageLabel, transcriptLabel } from './format';
import { countMatches } from './highlight';
import { canEditPool, personName } from './rules';
import TranscriptView from './TranscriptView.vue';
import type { Asset, Person } from './types';

const props = defineProps<{
  id: string;
}>();

const session = inject(SESSION_KEY);
const canEdit = computed(() => canEditPool(session?.value?.roles ?? []));

const { data, error, loading, run } = useAsync(async () => {
  const [detail, assets, people] = await Promise.all([
    poolApi.asset(props.id),
    poolApi.assets(),
    poolApi.people(),
  ]);
  return { detail, assets, people };
});

onMounted(run);
watch(() => props.id, () => void run());

// Editable copy of the people and tags; reset whenever the asset is (re)loaded.
const editPeople = ref<string[]>([]);
const editTags = ref('');
const saving = ref(false);
const editError = ref<string | null>(null);

watch(data, (value) => {
  if (value) {
    editPeople.value = [...value.detail.people];
    editTags.value = value.detail.tags.join(', ');
  }
});

const transcriptQuery = ref('');

const detail = computed(() => data.value?.detail ?? null);
const people = computed<Person[]>(() => data.value?.people ?? []);

function titleOf(id: string): string {
  return data.value?.assets.find((asset: Asset) => asset.id === id)?.title ?? id;
}

const matches = computed(() =>
  detail.value ? countMatches(detail.value.transcriptSegments, transcriptQuery.value) : 0,
);

function valueOf(event: Event): string {
  return (event.target as HTMLInputElement).value;
}

function togglePerson(personId: string, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked;
  editPeople.value = checked
    ? [...editPeople.value, personId]
    : editPeople.value.filter((candidate) => candidate !== personId);
}

function parseTags(text: string): string[] {
  return text
    .split(',')
    .map((tag) => tag.trim().toLowerCase())
    .filter((tag) => tag !== '');
}

async function save(): Promise<void> {
  editError.value = null;
  saving.value = true;
  try {
    await poolApi.updateTags(props.id, { tags: parseTags(editTags.value), people: [...editPeople.value] });
    await run();
  } catch (cause) {
    editError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}

const transcriptNote = computed(() => {
  switch (detail.value?.transcript) {
    case 'pending':
      return 'Das Transkript wird gerade erstellt.';
    case 'none':
      return 'Für dieses Material gibt es kein Transkript.';
    default:
      return null;
  }
});
</script>

<template>
  <div class="page">
    <RouterLink :to="{ name: 'pool' }" class="back-link">Zurück zum Pool</RouterLink>

    <LoadState :loading="loading" :error="error">
      <template v-if="detail">
        <header class="page-head">
          <div>
            <h2>{{ detail.title }}</h2>
            <div class="asset-badges">
              <span class="chip">{{ kindLabel(detail.kind) }}</span>
              <span class="chip" :class="`stage-${detail.stage}`">{{ stageLabel(detail.stage) }}</span>
            </div>
          </div>
          <RouterLink
            v-if="detail.stage === 'rendered'"
            :to="`/kalender?plan=${encodeURIComponent(detail.id)}`"
            class="plan-button"
          >Einplanen</RouterLink>
        </header>

        <section class="card">
          <dl class="asset-meta">
            <dt>Dauer</dt><dd>{{ formatDuration(detail.durationSeconds) }}</dd>
            <dt>Besitzer</dt><dd>{{ personName(people, detail.ownerId) }}</dd>
            <dt>Personen</dt><dd>{{ detail.people.map((id) => personName(people, id)).join(', ') || 'keine' }}</dd>
            <dt>Tags</dt><dd>{{ detail.tags.join(', ') || 'keine' }}</dd>
            <dt>Transkript</dt><dd>{{ transcriptLabel(detail.transcript) }}</dd>
            <dt>Erstellt</dt><dd>{{ formatCreatedAt(detail.createdAt) }}</dd>
            <dt v-if="detail.seriesId">Serie</dt><dd v-if="detail.seriesId">{{ detail.seriesId }}</dd>
          </dl>
          <p v-if="detail.sourceAssetId" class="source">
            Geschnitten aus:
            <RouterLink :to="{ name: 'pool-asset', params: { id: detail.sourceAssetId } }">{{ titleOf(detail.sourceAssetId) }}</RouterLink>
          </p>
        </section>

        <section v-if="canEdit" class="section">
          <h3 class="section-title">Personen und Tags</h3>
          <form class="card edit-form" @submit.prevent="save">
            <fieldset class="people">
              <legend>Personen</legend>
              <label v-for="entry in people" :key="entry.id" class="person-option">
                <input type="checkbox" :value="entry.id" :checked="editPeople.includes(entry.id)" @change="togglePerson(entry.id, $event)" />
                <span>{{ entry.name }}</span>
              </label>
            </fieldset>
            <!-- eslint-disable vue/no-deprecated-slot-attribute -->
            <md-outlined-text-field label="Tags (kommagetrennt)" :value="editTags" @input="editTags = valueOf($event)"></md-outlined-text-field>
            <!-- eslint-enable vue/no-deprecated-slot-attribute -->
            <p v-if="editError" class="form-error" role="alert">{{ editError }}</p>
            <div>
              <md-filled-button type="submit" :disabled="saving">Speichern</md-filled-button>
            </div>
          </form>
        </section>

        <section class="section">
          <h3 class="section-title">Geschnittene Clips</h3>
          <p v-if="detail.clips.length === 0" class="muted">Noch keine Clips aus diesem Material.</p>
          <ul v-else class="clip-list">
            <li v-for="clipId in detail.clips" :key="clipId">
              <RouterLink :to="{ name: 'pool-asset', params: { id: clipId } }">{{ titleOf(clipId) }}</RouterLink>
            </li>
          </ul>
        </section>

        <section class="section">
          <div class="transcript-head">
            <h3 class="section-title">Transkript</h3>
            <!-- eslint-disable vue/no-deprecated-slot-attribute -->
            <md-outlined-text-field
              data-testid="transcript-search"
              label="Im Transkript suchen"
              :value="transcriptQuery"
              @input="transcriptQuery = valueOf($event)"
            ></md-outlined-text-field>
            <!-- eslint-enable vue/no-deprecated-slot-attribute -->
            <span v-if="transcriptQuery.trim() !== ''" class="muted" data-testid="transcript-matches">
              {{ matches }} Treffer
            </span>
          </div>
          <p v-if="transcriptNote" class="card muted">{{ transcriptNote }}</p>
          <div v-else-if="detail.transcriptSegments.length === 0" class="card muted">Keine Segmente vorhanden.</div>
          <div v-else class="card">
            <TranscriptView :segments="detail.transcriptSegments" :people="people" :query="transcriptQuery" />
          </div>
        </section>
      </template>
    </LoadState>
  </div>
</template>

<style scoped>
.back-link {
  font-size: 14px;
}

.asset-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.stage-rendered {
  background: var(--md-sys-color-primary-container);
  color: var(--md-sys-color-on-primary-container);
  border-color: transparent;
}

.plan-button {
  display: inline-flex;
  align-items: center;
  padding: 10px 24px;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  text-decoration: none;
  font-weight: 500;
}

.asset-meta {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 6px 16px;
  margin: 0;
}

.asset-meta dt {
  color: var(--app-muted);
}

.asset-meta dd {
  margin: 0;
}

.source {
  margin: 16px 0 0;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.people {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 8px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.person-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.form-error {
  margin: 0;
  color: var(--md-sys-color-error);
}

.clip-list {
  margin: 0;
  padding-left: 20px;
}

.transcript-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}
</style>
