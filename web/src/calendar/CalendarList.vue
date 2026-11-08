<script setup lang="ts">
import type { CalendarItem } from '../api/posts';
import { STATUS_LABELS, type DayGroup } from './model';

// The list view: the days that hold posts, each with its posts in local time order. A phone shows it as the calendar.
defineProps<{
  groups: DayGroup[];
  selectedId?: string | null;
}>();

const emit = defineEmits<{ select: [id: string] }>();

function entryLabel(item: CalendarItem): string {
  return `${item.localTime} ${item.title}, ${STATUS_LABELS[item.status]}`;
}
</script>

<template>
  <section class="calendar-list" aria-label="Beiträge nach Tag">
    <p v-if="groups.length === 0" class="calendar-list__empty">Im gewählten Zeitraum gibt es keine Beiträge.</p>
    <div v-for="group in groups" :key="group.day" class="calendar-list__day" :data-day="group.day">
      <h3 class="calendar-list__heading">{{ group.label }}</h3>
      <ul class="calendar-list__items">
        <li v-for="item in group.items" :key="item.id">
          <button
            type="button"
            class="calendar-list__entry"
            :class="{ 'calendar-list__entry--selected': item.id === selectedId }"
            :aria-label="entryLabel(item)"
            :aria-pressed="item.id === selectedId ? 'true' : 'false'"
            @click="emit('select', item.id)"
          >
            <span class="calendar-list__time">{{ item.localTime }}</span>
            <span class="calendar-list__title">{{ item.title }}</span>
            <span class="calendar-list__meta">{{ item.channelName }} · {{ STATUS_LABELS[item.status] }}</span>
          </button>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.calendar-list {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-size: 14px;
}

.calendar-list__empty {
  margin: 0;
}

.calendar-list__heading {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 500;
}

.calendar-list__items {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.calendar-list__entry {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--md-sys-color-on-surface);
  font: inherit;
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.calendar-list__entry--selected {
  outline: 2px solid var(--md-sys-color-primary);
}

.calendar-list__time {
  font-weight: 500;
}

.calendar-list__title,
.calendar-list__meta {
  overflow-wrap: anywhere;
}

.calendar-list__meta {
  grid-column: 2;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 12px;
}
</style>
