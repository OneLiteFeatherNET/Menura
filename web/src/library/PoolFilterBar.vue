<script setup lang="ts">
// The filters of the pool. The bar only reports the chosen filters; the page decides when to ask the server again.
import FilterBar from '../components/ui/FilterBar.vue';
import AppSelect from '../components/ui/AppSelect.vue';
import AppTextField from '../components/ui/AppTextField.vue';
import { EMPTY_FILTERS, type PoolFilters, type PoolStage, type PoolUsage } from './pool';

const props = defineProps<{ filters: PoolFilters }>();
const emit = defineEmits<{ 'update:filters': [filters: PoolFilters] }>();

const ALL = 'all';

const scopeOptions = [
  { value: ALL, label: 'Alle Beiträge' },
  { value: 'mine', label: 'Nur meine' },
];
const stageOptions = [
  { value: ALL, label: 'Alle Stufen' },
  { value: 'raw', label: 'Rohmaterial' },
  { value: 'cut', label: 'Geschnitten' },
  { value: 'rendered', label: 'Gerendert' },
];
const usageOptions = [
  { value: ALL, label: 'Alle' },
  { value: 'used', label: 'Verwendet' },
  { value: 'never_used', label: 'Nie verwendet' },
];

function update(patch: Partial<PoolFilters>) {
  emit('update:filters', { ...props.filters, ...patch });
}

function reset() {
  emit('update:filters', { ...EMPTY_FILTERS });
}

const isChosen = () =>
  props.filters.mine || props.filters.tag.trim() !== '' || props.filters.stage !== '' || props.filters.usage !== '';
</script>

<template>
  <FilterBar label="Pool filtern" :resettable="isChosen()" @reset="reset">
    <AppSelect
      label="Beiträge"
      :options="scopeOptions"
      :model-value="filters.mine ? 'mine' : ALL"
      @update:model-value="(value: string) => update({ mine: value === 'mine' })"
    />
    <AppSelect
      label="Stufe"
      :options="stageOptions"
      :model-value="filters.stage || ALL"
      @update:model-value="(value: string) => update({ stage: value === ALL ? '' : (value as PoolStage) })"
    />
    <AppSelect
      label="Verwendung"
      :options="usageOptions"
      :model-value="filters.usage || ALL"
      @update:model-value="(value: string) => update({ usage: value === ALL ? '' : (value as PoolUsage) })"
    />
    <AppTextField
      label="Tag"
      type="search"
      :model-value="filters.tag"
      :maxlength="30"
      placeholder="z. B. speedrun"
      @update:model-value="(value: string) => update({ tag: value })"
    />
  </FilterBar>
</template>
