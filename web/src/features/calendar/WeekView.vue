<script setup lang="ts">
import { computed } from 'vue';
import { berlinDateKey, berlinTime, formatDayHeading, weekDays } from './dates';
import PublicationEntry from './PublicationEntry.vue';
import { outsideGridHours, placeInWeek, WEEK_HOURS, weekCellKey } from './grid';
import type { Publication } from './types';

const props = defineProps<{
  publications: Publication[];
  anchorKey: string;
  colours: Map<string, string>;
  selectedId: string | null;
}>();

defineEmits<{
  select: [publication: Publication];
}>();

const days = computed(() => weekDays(props.anchorKey));
const cells = computed(() => placeInWeek(props.publications, days.value));
const outside = computed(() => outsideGridHours(props.publications, days.value));

function entriesOf(day: string, hour: number): Publication[] {
  return cells.value.get(weekCellKey(day, hour)) ?? [];
}

function colourOf(publication: Publication): string | undefined {
  return publication.seriesId ? props.colours.get(publication.seriesId) : undefined;
}

function hourLabel(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`;
}
</script>

<template>
  <div class="week">
    <div class="week-grid" role="grid" aria-label="Woche">
      <div class="corner" aria-hidden="true"></div>
      <div v-for="day in days" :key="day" class="day-head" role="columnheader">
        {{ formatDayHeading(day) }}
      </div>
      <template v-for="hour in WEEK_HOURS" :key="hour">
        <div class="hour-label" role="rowheader">{{ hourLabel(hour) }}</div>
        <div
          v-for="day in days"
          :key="`${day}-${hour}`"
          class="cell"
          role="gridcell"
          :data-day="day"
          :data-hour="hour"
        >
          <PublicationEntry
            v-for="publication in entriesOf(day, hour)"
            :key="publication.id"
            :publication="publication"
            :colour="colourOf(publication)"
            :selected="publication.id === selectedId"
            @select="$emit('select', $event)"
          />
        </div>
      </template>
    </div>

    <section v-if="outside.length > 0" class="outside" aria-label="Außerhalb 08–23 Uhr">
      <h3 class="outside-title">Außerhalb 08–23 Uhr</h3>
      <ul class="outside-list">
        <li v-for="publication in outside" :key="publication.id">
          <span class="outside-day">{{ formatDayHeading(berlinDateKey(new Date(publication.publishAt))) }}</span>
          <span>{{ berlinTime(new Date(publication.publishAt)) }}</span>
          <PublicationEntry
            :publication="publication"
            :colour="colourOf(publication)"
            :selected="publication.id === selectedId"
            @select="$emit('select', $event)"
          />
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.week-grid {
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(120px, 1fr));
  overflow-x: auto;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: var(--app-card);
}

.corner {
  border-bottom: 1px solid var(--app-border);
}

.day-head {
  padding: 8px;
  border-bottom: 1px solid var(--app-border);
  font-size: 13px;
  font-weight: 600;
}

.hour-label {
  padding: 4px 6px;
  border-top: 1px solid var(--app-border);
  color: var(--app-muted);
  font-size: 12px;
}

.cell {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 52px;
  padding: 4px;
  border-top: 1px solid var(--app-border);
  border-left: 1px solid var(--app-border);
}

.outside {
  margin-top: 16px;
}

.outside-title {
  margin: 0 0 8px;
  font-size: 15px;
}

.outside-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.outside-list li {
  display: grid;
  grid-template-columns: 40px 52px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.outside-day {
  color: var(--app-muted);
  font-size: 13px;
}
</style>
