<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue';
import { SESSION_KEY } from '../../auth/session';
import LoadState from '../../components/LoadState.vue';
import { useAsync } from '../../composables/useAsync';
import { formatCount } from '../../domain/format';
import { poolApi } from './api';
import AddAssetDialog from './AddAssetDialog.vue';
import { formatCreatedAt, formatDuration, kindLabel, stageLabel, transcriptLabel } from './format';
import { canEditPool, personName, topTags } from './rules';
import { useDebouncedValue } from './useDebouncedValue';
import type { Asset, AssetFilter, AssetKind, AssetStage } from './types';

const session = inject(SESSION_KEY);
const canEdit = computed(() => canEditPool(session?.value?.roles ?? []));

// Filter state. 'all' is the unfiltered value of each select.
const kind = ref<'all' | AssetKind>('all');
const stage = ref<'all' | AssetStage>('all');
const person = ref<'all' | 'me' | string>('all');
const tag = ref<string | null>(null);
const searchText = ref('');
const search = useDebouncedValue(searchText);

// The unfiltered pool: people for the names and the person select, and every asset for the tag chips.
const catalogue = useAsync(async () => {
  const [people, assets] = await Promise.all([poolApi.people(), poolApi.assets()]);
  return { people, assets };
});

const filter = computed<AssetFilter>(() => ({
  kind: kind.value === 'all' ? undefined : kind.value,
  stage: stage.value === 'all' ? undefined : stage.value,
  ownerId: person.value === 'me' ? 'me' : undefined,
  personId: person.value === 'all' || person.value === 'me' ? undefined : person.value,
  tag: tag.value ?? undefined,
  q: search.value.trim() || undefined,
}));

const results = useAsync(() => poolApi.assets(filter.value));

watch(filter, () => void results.run(), { immediate: true });

onMounted(() => void catalogue.run());

const tagChips = computed(() => topTags(catalogue.data.value?.assets ?? []));
const people = computed(() => catalogue.data.value?.people ?? []);
const count = computed(() => results.data.value?.length ?? 0);
const poolIsEmpty = computed(() => (catalogue.data.value?.assets.length ?? 0) === 0);

const addOpen = ref(false);

function toggleTag(name: string): void {
  tag.value = tag.value === name ? null : name;
}

function valueOf(event: Event): string {
  return (event.target as HTMLInputElement).value;
}

function onAdded(): void {
  void catalogue.run();
  void results.run();
}

function ownerName(asset: Asset): string {
  return personName(people.value, asset.ownerId);
}

function peopleNames(asset: Asset): string {
  return asset.people.map((id) => personName(people.value, id)).join(', ') || 'keine';
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Pool</h2>
        <p class="page-lead">Rohmaterial und Clips, mit Personen, Tags und Transkript.</p>
        <p class="muted" data-testid="pool-count">{{ formatCount(count) }} {{ count === 1 ? 'Eintrag' : 'Einträge' }}</p>
      </div>
      <md-filled-button v-if="canEdit" @click="addOpen = true">Material hinzufügen</md-filled-button>
    </header>

    <section class="filters card" aria-label="Filter">
      <div class="filter-row">
        <!-- eslint-disable vue/no-deprecated-slot-attribute -->
        <md-outlined-select data-testid="filter-kind" label="Art" :value="kind" @change="kind = valueOf($event) as 'all' | AssetKind">
          <md-select-option value="all" headline="Alle"></md-select-option>
          <md-select-option value="raw" headline="Rohmaterial"></md-select-option>
          <md-select-option value="clip" headline="Clips"></md-select-option>
        </md-outlined-select>
        <md-outlined-select data-testid="filter-stage" label="Stufe" :value="stage" @change="stage = valueOf($event) as 'all' | AssetStage">
          <md-select-option value="all" headline="Alle"></md-select-option>
          <md-select-option value="raw" headline="Roh"></md-select-option>
          <md-select-option value="cut" headline="Geschnitten"></md-select-option>
          <md-select-option value="rendered" headline="Fertig gerendert"></md-select-option>
        </md-outlined-select>
        <md-outlined-select data-testid="filter-person" label="Person" :value="person" @change="person = valueOf($event)">
          <md-select-option value="all" headline="Alle"></md-select-option>
          <md-select-option value="me" headline="Meine"></md-select-option>
          <md-select-option
            v-for="entry in people"
            :key="entry.id"
            :value="entry.id"
            :headline="entry.name"
          ></md-select-option>
        </md-outlined-select>
        <md-outlined-text-field data-testid="filter-search" label="Suche" :value="searchText" @input="searchText = valueOf($event)"></md-outlined-text-field>
        <!-- eslint-enable vue/no-deprecated-slot-attribute -->
      </div>
      <div v-if="tagChips.length > 0" class="tag-row" aria-label="Häufige Tags">
        <button
          v-for="name in tagChips"
          :key="name"
          type="button"
          class="chip tag-chip"
          data-testid="tag-chip"
          :aria-pressed="tag === name"
          :class="{ 'tag-chip-active': tag === name }"
          @click="toggleTag(name)"
        >{{ name }}</button>
      </div>
    </section>

    <LoadState :loading="catalogue.loading.value || results.loading.value" :error="results.error.value ?? catalogue.error.value">
      <p v-if="count === 0" class="card muted empty-state">
        {{ poolIsEmpty ? 'Der Pool ist noch leer.' : 'Keine Treffer für diese Filter.' }}
      </p>
      <div v-else class="asset-grid">
        <article v-for="asset in results.data.value" :key="asset.id" class="card asset-card">
          <RouterLink :to="{ name: 'pool-asset', params: { id: asset.id } }" class="asset-title">{{ asset.title }}</RouterLink>
          <div class="asset-badges">
            <span class="chip">{{ kindLabel(asset.kind) }}</span>
            <span class="chip stage" :class="`stage-${asset.stage}`">{{ stageLabel(asset.stage) }}</span>
          </div>
          <dl class="asset-meta">
            <dt>Dauer</dt><dd>{{ formatDuration(asset.durationSeconds) }}</dd>
            <dt>Besitzer</dt><dd>{{ ownerName(asset) }}</dd>
            <dt>Personen</dt><dd>{{ peopleNames(asset) }}</dd>
            <dt>Transkript</dt><dd>{{ transcriptLabel(asset.transcript) }}</dd>
            <dt>Erstellt</dt><dd>{{ formatCreatedAt(asset.createdAt) }}</dd>
          </dl>
          <div class="asset-tags">
            <span v-for="name in asset.tags" :key="name" class="chip">{{ name }}</span>
          </div>
        </article>
      </div>
    </LoadState>

    <AddAssetDialog
      v-if="canEdit"
      :open="addOpen"
      :people="people"
      @close="addOpen = false"
      @created="onAdded"
    />
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  cursor: pointer;
  background: transparent;
  font: inherit;
}

.tag-chip-active {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  border-color: transparent;
}

.empty-state {
  margin: 0;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr));
  gap: 16px;
}

.asset-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.asset-title {
  font-size: 18px;
  line-height: 24px;
  font-weight: 500;
  color: var(--md-sys-color-on-surface);
  text-decoration: none;
}

.asset-badges,
.asset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.stage-rendered {
  background: var(--md-sys-color-primary-container);
  color: var(--md-sys-color-on-primary-container);
  border-color: transparent;
}

.asset-meta {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 4px 12px;
  margin: 0;
  font-size: 14px;
}

.asset-meta dt {
  color: var(--app-muted);
}

.asset-meta dd {
  margin: 0;
}
</style>
