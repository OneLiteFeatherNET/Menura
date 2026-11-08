<script setup lang="ts">
import { computed } from 'vue';
import { dayNumber, monthGrid, monthOf } from './dates';
import PublicationEntry from './PublicationEntry.vue';
import { byPublishTime, groupByBerlinDay, MONTH_VISIBLE_ENTRIES } from './grid';
import type { Publication } from './types';

const props = defineProps<{
  publications: Publication[];
  anchorKey: string;
  colours: Map<string, string>;
  selectedId: string | null;
}>();

const emit = defineEmits<{
  select: [publication: Publication];
  openDay: [dayKey: string];
}>();

const cells = computed(() => monthGrid(props.anchorKey));
const byDay = computed(() => groupByBerlinDay(props.publications));
const weekdayNames = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

function entriesOf(day: string): Publication[] {
  return (byDay.value.get(day) ?? []).slice().sort(byPublishTime);
}

function colourOf(publication: Publication): string | undefined {
  return publication.seriesId ? props.colours.get(publication.seriesId) : undefined;
}

function isInMonth(day: string): boolean {
  return monthOf(day) === monthOf(props.anchorKey);
}
</script>

<template>
  <div class="month">
    <div class="month-grid" role="grid" aria-label="Monat">
      <div v-for="name in weekdayNames" :key="name" class="weekday-head" role="columnheader">{{ name }}</div>
      <div
        v-for="day in cells"
        :key="day"
        class="month-cell"
        :class="{ outside: !isInMonth(day) }"
        role="gridcell"
        :data-day="day"
      >
        <span class="day-number">{{ dayNumber(day) }}</span>
        <PublicationEntry
          v-for="publication in entriesOf(day).slice(0, MONTH_VISIBLE_ENTRIES)"
          :key="publication.id"
          :publication="publication"
          :colour="colourOf(publication)"
          :selected="publication.id === selectedId"
          @select="emit('select', $event)"
        />
        <button
          v-if="entriesOf(day).length > MONTH_VISIBLE_ENTRIES"
          type="button"
          class="more"
          @click="emit('openDay', day)"
        >
          +{{ entriesOf(day).length - MONTH_VISIBLE_ENTRIES }} weitere
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  overflow-x: auto;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: var(--app-card);
}

.weekday-head {
  padding: 8px;
  border-bottom: 1px solid var(--app-border);
  font-size: 13px;
  font-weight: 600;
  text-align: center;
}

.month-cell {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 112px;
  min-width: 120px;
  padding: 6px;
  border-top: 1px solid var(--app-border);
  border-left: 1px solid var(--app-border);
}

.month-cell.outside {
  color: var(--app-muted);
  background: color-mix(in srgb, var(--app-card) 70%, var(--md-sys-color-surface-variant));
}

.day-number {
  font-size: 13px;
  font-weight: 600;
}

.more {
  justify-self: start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--md-sys-color-primary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
</style>
