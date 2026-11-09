<script setup lang="ts">
import { computed } from 'vue';
import type { CalendarItem } from '../api/posts';
import { dayLabel, groupByDay, STATUS_LABELS } from './model';

// The month and week grids: one row per week, seven day cells from Monday. The rows come from the page (blank cells are
// null); the posts of each day are grouped by their local date, so the cell and the time are the server's values.
const props = defineProps<{
  weeks: (string | null)[][];
  items: CalendarItem[];
  selectedId?: string | null;
}>();

const emit = defineEmits<{ select: [id: string] }>();

const WEEKDAY_NAMES = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];
const WEEKDAY_SHORT = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

const byDay = computed(() => groupByDay(props.items));

function dayItems(day: string | null): CalendarItem[] {
  return day ? (byDay.value.get(day) ?? []) : [];
}

function dayNumber(day: string): number {
  return Number(day.slice(8, 10));
}
</script>

<template>
  <div class="calendar-grid" role="region" aria-label="Kalenderraster">
    <div class="calendar-grid__head" aria-hidden="true">
      <span v-for="(name, index) in WEEKDAY_SHORT" :key="name" class="calendar-grid__weekday" :title="WEEKDAY_NAMES[index]">
        {{ name }}
      </span>
    </div>
    <div v-for="(week, rowIndex) in weeks" :key="rowIndex" class="calendar-grid__row">
      <template v-for="(day, columnIndex) in week" :key="columnIndex">
        <div
          v-if="day"
          class="calendar-grid__cell"
          role="group"
          :aria-label="dayLabel(day)"
          :data-day="day"
        >
          <span class="calendar-grid__day" aria-hidden="true">{{ dayNumber(day) }}</span>
          <button
            v-for="item in dayItems(day)"
            :key="item.id"
            type="button"
            class="calendar-grid__entry"
            :class="{ 'calendar-grid__entry--selected': item.id === selectedId }"
            :aria-pressed="item.id === selectedId ? 'true' : 'false'"
            @click="emit('select', item.id)"
          >
            <span class="calendar-grid__time">{{ item.localTime }}</span>
            <span class="calendar-grid__title">{{ item.title }}</span>
            <span class="calendar-grid__status">{{ STATUS_LABELS[item.status] }}</span>
          </button>
        </div>
        <div v-else class="calendar-grid__cell calendar-grid__cell--blank" aria-hidden="true"></div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.calendar-grid {
  min-width: 0;
  overflow-x: auto;
  font-size: 14px;
}

.calendar-grid__head,
.calendar-grid__row {
  display: grid;
  grid-template-columns: repeat(7, minmax(110px, 1fr));
  min-width: 770px;
}

.calendar-grid__weekday {
  padding: 6px 8px;
  color: var(--md-sys-color-on-surface-variant);
  font-weight: 500;
}

.calendar-grid__cell {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 96px;
  padding: 6px;
  border: 1px solid var(--md-sys-color-outline-variant);
  min-width: 0;
}

.calendar-grid__cell--blank {
  background: var(--md-sys-color-surface-container-low);
}

.calendar-grid__day {
  color: var(--md-sys-color-on-surface-variant);
}

.calendar-grid__entry {
  display: grid;
  gap: 2px;
  padding: 4px 6px;
  border: 0;
  border-radius: 6px;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  font: inherit;
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.calendar-grid__entry--selected {
  outline: 2px solid var(--md-sys-color-primary);
}

.calendar-grid__time {
  font-weight: 500;
}

.calendar-grid__title,
.calendar-grid__status {
  overflow-wrap: anywhere;
}

.calendar-grid__status {
  font-size: 12px;
}
</style>
