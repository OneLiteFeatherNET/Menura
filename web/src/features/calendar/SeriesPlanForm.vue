<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink } from 'vue-router';
import type { Channel } from '../../api/types';
import { calendarApi } from './api';
import { errorMessage } from '../../api/client';
import { publicationErrorMessage } from './labels';

const props = defineProps<{
  seriesId: string;
}>();

const open = ref(false);
const channels = ref<Channel[]>([]);
const channelIds = ref<string[]>([]);
const count = ref(3);
const loadingChannels = ref(false);
const submitting = ref(false);
const error = ref<string | null>(null);
const planned = ref<number | null>(null);

const countValid = computed(() => Number.isInteger(count.value) && count.value >= 1 && count.value <= 10);

async function toggle(): Promise<void> {
  open.value = !open.value;
  if (!open.value || channels.value.length > 0) {
    return;
  }
  loadingChannels.value = true;
  try {
    channels.value = await calendarApi.channels();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loadingChannels.value = false;
  }
}

async function submit(): Promise<void> {
  error.value = null;
  planned.value = null;
  if (channelIds.value.length === 0) {
    error.value = 'Bitte mindestens einen Kanal wählen.';
    return;
  }
  if (!countValid.value) {
    error.value = 'Bitte eine Anzahl zwischen 1 und 10 wählen.';
    return;
  }
  submitting.value = true;
  try {
    const created = await calendarApi.planSeries(props.seriesId, {
      channelIds: [...channelIds.value],
      count: count.value,
    });
    planned.value = new Set(created.map((publication) => publication.publishAt)).size;
  } catch (cause) {
    error.value = publicationErrorMessage(cause, 'series');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="series-plan">
    <md-filled-tonal-button type="button" :aria-expanded="open" @click="toggle">
      Nächste Folgen einplanen
    </md-filled-tonal-button>

    <form v-if="open" class="plan-form" @submit.prevent="submit">
      <p v-if="loadingChannels" class="muted" role="status">Kanäle werden geladen …</p>
      <fieldset class="channels">
        <legend>Kanäle</legend>
        <label v-for="channel in channels" :key="channel.id" class="check">
          <input v-model="channelIds" type="checkbox" name="channel" :value="channel.id" />
          {{ channel.name }}
        </label>
      </fieldset>
      <label class="count">
        <span>Anzahl Folgen (1–10)</span>
        <input v-model.number="count" type="number" name="count" min="1" max="10" step="1" />
      </label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="planned !== null" class="ok" role="status">
        {{ planned }} {{ planned === 1 ? 'Folge' : 'Folgen' }} eingeplant.
        <RouterLink :to="{ path: '/kalender', query: { ansicht: 'liste', serie: seriesId } }">
          Im Kalender ansehen
        </RouterLink>
      </p>
      <md-filled-button type="submit" :disabled="submitting">Einplanen</md-filled-button>
    </form>
  </div>
</template>

<style scoped>
.series-plan {
  display: grid;
  gap: 12px;
  justify-items: start;
}

.plan-form {
  display: grid;
  gap: 12px;
  width: min(460px, 100%);
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: var(--app-card);
}

.channels {
  display: grid;
  gap: 6px;
  border: 0;
  padding: 0;
  margin: 0;
}

.channels legend,
.count span {
  color: var(--app-muted);
  font-size: 13px;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.count {
  display: grid;
  gap: 4px;
}

.count input {
  font: inherit;
  width: 96px;
  padding: 8px 10px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 8px;
  background: var(--app-card);
  color: inherit;
}

.error {
  margin: 0;
  color: var(--md-sys-color-error);
}

.ok {
  margin: 0;
  color: var(--md-sys-color-primary);
}

.muted {
  margin: 0;
  color: var(--app-muted);
}
</style>
