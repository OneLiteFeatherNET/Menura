<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api } from '../api/client';
import LoadState from '../components/LoadState.vue';
import { useAsync } from '../composables/useAsync';

const { data, error, loading, run } = useAsync(() => api.renderBundle());

onMounted(run);

const json = computed(() => (data.value ? JSON.stringify(data.value, null, 2) : ''));
const copyState = ref<'idle' | 'copied' | 'failed'>('idle');

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(json.value);
    copyState.value = 'copied';
  } catch {
    copyState.value = 'failed';
  }
}
</script>

<template>
  <div class="page">
    <RouterLink to="/brand-kit" class="back">← Brand-Kit</RouterLink>

    <header class="page-head">
      <div>
        <h2>Render-Bundle</h2>
        <p class="page-lead">
          Das Paket, das der externe Renderer (Schnitt-Tool) liest: Brand-Kit, aktive Serien und Kanäle.
        </p>
      </div>
      <md-filled-button :disabled="!data" @click="copy">In die Zwischenablage</md-filled-button>
    </header>

    <p v-if="copyState === 'copied'" class="muted" role="status">Kopiert.</p>
    <p v-else-if="copyState === 'failed'" class="card card-error" role="alert">
      Kopieren ist in diesem Browser nicht erlaubt. Den Text bitte von Hand markieren.
    </p>

    <LoadState :loading="loading" :error="error">
      <pre class="bundle" tabindex="0">{{ json }}</pre>
    </LoadState>
  </div>
</template>

<style scoped>
.back {
  font-size: 15px;
}

.bundle {
  margin: 0;
  padding: 20px 24px;
  overflow: auto;
  max-height: 70vh;
  border-radius: 20px;
  border: 1px solid var(--app-border);
  background: var(--app-card);
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 15px;
  line-height: 22px;
  white-space: pre;
}
</style>
