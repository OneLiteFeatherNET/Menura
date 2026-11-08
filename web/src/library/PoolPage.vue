<script setup lang="ts">
// The pool page: loads the pool for the chosen filters through the API and shows it with the shared components.
import { ref, watch } from 'vue';
import PageHeader from '../components/ui/PageHeader.vue';
import PoolFilterBar from './PoolFilterBar.vue';
import PoolList from './PoolList.vue';
import { EMPTY_FILTERS, fetchPool, type PoolFilters, type PoolItem } from './pool';

const filters = ref<PoolFilters>({ ...EMPTY_FILTERS });
const items = ref<PoolItem[]>([]);
const nextCursor = ref<string | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
let request = 0;

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
</script>

<template>
  <div class="pool-page">
    <PageHeader title="Content-Pool" description="Rohmaterial und Clips des Teams, durchsuchbar nach Person, Tag und Stufe." />
    <PoolFilterBar v-model:filters="filters" />
    <PoolList
      :items="items"
      :status="status"
      :has-more="nextCursor !== null"
      error-message="Der Content-Pool konnte nicht geladen werden."
      @more="load(nextCursor)"
      @retry="load()"
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
</style>
