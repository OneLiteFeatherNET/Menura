<script setup lang="ts">
import { computed } from 'vue';
import type { EqualAge, EqualAgeBy } from '../../api/analytics';
import {
  EQUAL_AGE_GROUPS,
  EQUAL_AGE_METRICS,
  EQUAL_AGE_OFFSETS,
  NO_POSTS_TEXT,
  NO_POSTS_TITLE,
  type EqualAgeChoice,
} from '../../analytics/format';
import AppSelect from '../ui/AppSelect.vue';
import EmptyState from '../ui/EmptyState.vue';
import LoadState from '../ui/LoadState.vue';
import SlotChart from './SlotChart.vue';

// The equal-age view: the median of one metric at one offset after publication, per hour or per weekday. The choice is
// reported as an event with the whole choice; the page loads the answer.
const props = defineProps<{
  status: 'loading' | 'error' | 'ready';
  equalAge: EqualAge | null;
  choice: EqualAgeChoice;
  errorMessage?: string;
}>();

const emit = defineEmits<{ retry: []; change: [choice: EqualAgeChoice] }>();

const hasPosts = computed(() => (props.equalAge?.slots ?? []).some((slot) => slot.posts > 0));

function changeBy(value: string) {
  emit('change', { ...props.choice, by: value as EqualAgeBy });
}

function changeOffset(value: string) {
  emit('change', { ...props.choice, offsetHours: Number(value) });
}

function changeMetric(value: string) {
  emit('change', { ...props.choice, metric: value });
}
</script>

<template>
  <section class="analytics-panel">
    <h2 class="analytics-panel__heading">Uhrzeit/Wochentag</h2>
    <p class="analytics-panel__text">
      Median je Zeitfenster der Veröffentlichung in der Zeitzone Europe/Berlin, jeweils zu dem gewählten Abstand.
    </p>

    <div class="analytics-panel__controls">
      <AppSelect label="Gruppierung" :options="EQUAL_AGE_GROUPS" :model-value="choice.by" @update:model-value="changeBy" />
      <AppSelect
        label="Abstand zur Veröffentlichung"
        :options="EQUAL_AGE_OFFSETS"
        :model-value="String(choice.offsetHours)"
        @update:model-value="changeOffset"
      />
      <AppSelect label="Kennzahl" :options="EQUAL_AGE_METRICS" :model-value="choice.metric" @update:model-value="changeMetric" />
    </div>

    <LoadState :status="status" :error-message="errorMessage" @retry="emit('retry')">
      <template v-if="equalAge">
        <EmptyState v-if="!hasPosts" :title="NO_POSTS_TITLE" :description="NO_POSTS_TEXT" />
        <SlotChart
          v-else
          caption="Median je Zeitfenster"
          :by="equalAge.by"
          :slots="equalAge.slots"
        />
      </template>
    </LoadState>
  </section>
</template>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.analytics-panel__heading {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  line-height: 24px;
}

.analytics-panel__text {
  margin: 0;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.analytics-panel__controls {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  min-width: 0;
}
</style>
