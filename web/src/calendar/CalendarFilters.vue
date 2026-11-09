<script setup lang="ts">
import AppSelect, { type SelectOption } from '../components/ui/AppSelect.vue';
import { STATUS_LABELS, type CalendarFilters } from './model';

// The three filters of the calendar: channel, series and status. Each choice is reported as the whole filter set; an
// empty value means 'all', and the page sends only the filters that are set.
const props = defineProps<{
  modelValue: CalendarFilters;
  channels: SelectOption[];
  series: SelectOption[];
}>();

const emit = defineEmits<{ 'update:modelValue': [filters: CalendarFilters] }>();

const statusOptions: SelectOption[] = [
  { value: '', label: 'Alle Status' },
  ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label })),
];

function update(patch: Partial<CalendarFilters>) {
  emit('update:modelValue', { ...props.modelValue, ...patch });
}
</script>

<template>
  <div class="calendar-filters" role="group" aria-label="Filter">
    <AppSelect
      label="Kanal"
      :options="[{ value: '', label: 'Alle Kanäle' }, ...channels]"
      :model-value="modelValue.channelId"
      @update:model-value="(value: string) => update({ channelId: value })"
    />
    <AppSelect
      label="Serie"
      :options="[{ value: '', label: 'Alle Serien' }, ...series]"
      :model-value="modelValue.seriesId"
      @update:model-value="(value: string) => update({ seriesId: value })"
    />
    <AppSelect
      label="Status"
      :options="statusOptions"
      :model-value="modelValue.status"
      @update:model-value="(value: string) => update({ status: value as CalendarFilters['status'] })"
    />
  </div>
</template>

<style scoped>
.calendar-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  min-width: 0;
}
</style>
