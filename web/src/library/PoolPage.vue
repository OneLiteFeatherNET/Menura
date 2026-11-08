<script setup lang="ts">
// The pool page: loads the pool for the chosen filters through the API and shows it with the shared components.
// Editors also upload files and transcripts from here; the other roles only read the pool.
import { ref, watch } from 'vue';
import { fetchSession } from '../auth/session';
import PageHeader from '../components/ui/PageHeader.vue';
import PoolFilterBar from './PoolFilterBar.vue';
import PoolList from './PoolList.vue';
import { EMPTY_FILTERS, fetchPool, type PoolFilters, type PoolItem } from './pool';
import TranscriptDialog from './upload/TranscriptDialog.vue';
import UploadDialog from './upload/UploadDialog.vue';

const filters = ref<PoolFilters>({ ...EMPTY_FILTERS });
const items = ref<PoolItem[]>([]);
const nextCursor = ref<string | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
const canEdit = ref(false);
const uploadOpen = ref(false);
const transcriptItem = ref<PoolItem | null>(null);
let request = 0;

fetchSession()
  .then((session) => {
    canEdit.value = session?.roles.includes('EDITOR') ?? false;
  })
  .catch(() => {
    canEdit.value = false;
  });

// Each change of the filters starts again from the first page; a response of an older request is ignored.
async function load(cursor: string | null = null) {
  const current = ++request;
  status.value = 'loading';
  try {
    const page = await fetchPool(filters.value, cursor);
    if (current !== request) return;
    items.value = cursor ? [...items.value, ...page.items] : page.items;
    nextCursor.value = page.nextCursor;
    status.value = 'ready';
  } catch {
    if (current !== request) return;
    status.value = 'error';
  }
}

watch(filters, () => load(), { deep: true });
load();

function openTranscript(itemId: string) {
  transcriptItem.value = items.value.find((item) => item.id === itemId) ?? null;
}
</script>

<template>
  <div class="pool-page">
    <PageHeader title="Content-Pool" description="Rohmaterial und Clips des Teams, durchsuchbar nach Person, Tag und Stufe.">
      <button v-if="canEdit" type="button" class="pool-page__upload" @click="uploadOpen = true">Hochladen</button>
    </PageHeader>
    <PoolFilterBar v-model:filters="filters" />
    <PoolList
      :items="items"
      :status="status"
      :has-more="nextCursor !== null"
      :can-edit="canEdit"
      error-message="Der Content-Pool konnte nicht geladen werden."
      @more="load(nextCursor)"
      @retry="load()"
      @transcript="openTranscript"
    />
    <UploadDialog :open="uploadOpen" @close="uploadOpen = false" @completed="load()" />
    <TranscriptDialog
      :open="transcriptItem !== null"
      :raw-item-id="transcriptItem?.id ?? null"
      :item-title="transcriptItem?.title ?? 'Ohne Titel'"
      @close="transcriptItem = null"
      @uploaded="load()"
    />
  </div>
</template>

<style scoped>
.pool-page {
  display: grid;
  gap: 16px;
  min-width: 0;
  max-width: 100%;
}

.pool-page__upload {
  min-height: 40px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.pool-page__upload:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
