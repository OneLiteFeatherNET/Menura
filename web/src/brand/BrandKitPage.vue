<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchBrandKit, type BrandKitVersion } from './brandKit';
import BrandKitView from './BrandKitView.vue';

// Loads the newest brand kit version for the signed-in visitor and shows it. Reading is open to every signed-in role.
const status = ref<'loading' | 'error' | 'ready'>('loading');
const version = ref<BrandKitVersion | null>(null);

async function load() {
  status.value = 'loading';
  try {
    version.value = await fetchBrandKit();
    status.value = 'ready';
  } catch {
    status.value = 'error';
  }
}

onMounted(load);
</script>

<template>
  <BrandKitView :status="status" :version="version" @retry="load" />
</template>
