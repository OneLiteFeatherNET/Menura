<script setup lang="ts">
import { VIEW_LABELS, type CalendarView } from './model';

// The period navigation and the view switch. It shows the period label it receives and reports every step as an event;
// the page decides which period that is.
defineProps<{
  view: CalendarView;
  label: string;
  canPlan: boolean;
}>();

const emit = defineEmits<{
  'update:view': [view: CalendarView];
  previous: [];
  next: [];
  today: [];
  plan: [];
}>();

const views: CalendarView[] = ['month', 'week', 'list'];
</script>

<template>
  <div class="calendar-toolbar">
    <div class="calendar-toolbar__nav">
      <button type="button" class="calendar-toolbar__button" aria-label="Vorheriger Zeitraum" @click="emit('previous')">‹</button>
      <button type="button" class="calendar-toolbar__button" @click="emit('today')">Heute</button>
      <button type="button" class="calendar-toolbar__button" aria-label="Nächster Zeitraum" @click="emit('next')">›</button>
      <p class="calendar-toolbar__label" aria-live="polite">{{ label }}</p>
    </div>
    <div class="calendar-toolbar__actions">
      <div class="calendar-toolbar__views" role="group" aria-label="Ansicht">
        <button
          v-for="option in views"
          :key="option"
          type="button"
          class="calendar-toolbar__button"
          :aria-pressed="view === option ? 'true' : 'false'"
          @click="emit('update:view', option)"
        >
          {{ VIEW_LABELS[option] }}
        </button>
      </div>
      <button v-if="canPlan" type="button" class="calendar-toolbar__primary" @click="emit('plan')">Beitrag planen</button>
    </div>
  </div>
</template>

<style scoped>
.calendar-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.calendar-toolbar__nav,
.calendar-toolbar__actions,
.calendar-toolbar__views {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.calendar-toolbar__label {
  margin: 0 0 0 8px;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.calendar-toolbar__button,
.calendar-toolbar__primary {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.calendar-toolbar__button[aria-pressed='true'] {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.calendar-toolbar__primary {
  background: var(--md-sys-color-primary);
  border-color: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
}
</style>
