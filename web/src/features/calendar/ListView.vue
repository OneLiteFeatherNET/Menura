<script setup lang="ts">
import { computed } from 'vue';
import { formatDayHeading } from './dates';
import PublicationEntry from './PublicationEntry.vue';
import { groupByBerlinDay } from './grid';
import type { Publication } from './types';

const props = defineProps<{
  publications: Publication[];
  colours: Map<string, string>;
  selectedId: string | null;
}>();

defineEmits<{
  select: [publication: Publication];
}>();

const days = computed(() => [...groupByBerlinDay(props.publications).entries()]);

function colourOf(publication: Publication): string | undefined {
  return publication.seriesId ? props.colours.get(publication.seriesId) : undefined;
}
</script>

<template>
  <div class="agenda">
    <p v-if="days.length === 0" class="muted">Keine Veröffentlichungen in diesem Zeitraum.</p>
    <section v-for="[day, items] in days" :key="day" class="agenda-day" :data-agenda-day="day">
      <h3 class="agenda-heading">{{ formatDayHeading(day) }}</h3>
      <div class="agenda-items">
        <PublicationEntry
          v-for="publication in items"
          :key="publication.id"
          :publication="publication"
          :colour="colourOf(publication)"
          :selected="publication.id === selectedId"
          @select="$emit('select', $event)"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.agenda {
  display: grid;
  gap: 16px;
}

.agenda-heading {
  margin: 0 0 8px;
  font-size: 15px;
}

.agenda-items {
  display: grid;
  gap: 8px;
  max-width: 560px;
}
</style>
