<script setup lang="ts">
// The personal calendar feed: a read-only subscription address for calendar apps. The address carries the only
// credential of the feed, so the server shows it once; this panel keeps it on screen only while the page is open.
defineProps<{
  feedUrl: string | null;
  busy: boolean;
  error?: string;
}>();

const emit = defineEmits<{ issue: []; revoke: [] }>();
</script>

<template>
  <section class="feed-panel" aria-labelledby="feed-panel-title">
    <h2 id="feed-panel-title" class="feed-panel__title">Kalender-Abo</h2>
    <p class="feed-panel__text">
      Mit dieser Adresse übernimmt Ihr Kalender die geplanten Beiträge. Die Adresse lässt sich nur lesen. Wer sie kennt, sieht
      die Termine; erstellen Sie eine neue Adresse, wenn sie weitergegeben wurde.
    </p>

    <div v-if="feedUrl" class="feed-panel__address">
      <label class="feed-panel__label" for="feed-panel-url">Feed-Adresse</label>
      <input id="feed-panel-url" class="feed-panel__input" type="text" readonly :value="feedUrl" />
      <p class="feed-panel__hint">Die Adresse wird nur jetzt angezeigt.</p>
    </div>

    <p v-if="error" class="feed-panel__error" role="alert">{{ error }}</p>

    <div class="feed-panel__actions">
      <button type="button" class="feed-panel__button" :disabled="busy" @click="emit('issue')">
        {{ feedUrl ? 'Neue Adresse erstellen' : 'Kalender-Adresse erstellen' }}
      </button>
      <button type="button" class="feed-panel__button" :disabled="busy" @click="emit('revoke')">
        Adresse sperren
      </button>
    </div>
  </section>
</template>

<style scoped>
.feed-panel {
  display: grid;
  gap: 10px;
  min-width: 0;
  font-size: 14px;
}

.feed-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.feed-panel__text,
.feed-panel__hint,
.feed-panel__error {
  margin: 0;
}

.feed-panel__address {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.feed-panel__label {
  font-weight: 500;
}

.feed-panel__input {
  width: 100%;
  min-height: 40px;
  padding: 0 8px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 6px;
  font: inherit;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  box-sizing: border-box;
}

.feed-panel__error {
  color: var(--md-sys-color-error);
}

.feed-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feed-panel__button {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.feed-panel__button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
