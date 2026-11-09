<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PostVisibility } from '../api/posts';
import AppSelect, { type SelectOption } from '../components/ui/AppSelect.vue';
import { VISIBILITY_LABELS, declarationError, type DeclarationForm } from './model';

// The YouTube declaration of a post: its visibility, whether it is made for kids, and whether it is planned as a Short.
// YouTube refuses the scheduling until all of them are given, so the form asks for the two answers before it sends them.
const props = withDefaults(
  defineProps<{
    visibility: PostVisibility | null;
    madeForKids: boolean | null;
    plannedShort: boolean;
    busy?: boolean;
    error?: string;
  }>(),
  { busy: false, error: undefined },
);

const emit = defineEmits<{
  save: [declaration: { visibility: PostVisibility; madeForKids: boolean; planShort: boolean }];
}>();

const visibilityOptions: SelectOption[] = (Object.keys(VISIBILITY_LABELS) as PostVisibility[]).map((value) => ({
  value,
  label: VISIBILITY_LABELS[value],
}));

const form = ref<DeclarationForm>({
  visibility: props.visibility ?? '',
  madeForKids: props.madeForKids,
  plannedShort: props.plannedShort,
});
const formError = ref<string | undefined>(undefined);
const madeForKidsValue = computed(() => (form.value.madeForKids === null ? '' : form.value.madeForKids ? 'yes' : 'no'));

function chooseMadeForKids(value: string) {
  form.value = { ...form.value, madeForKids: value === 'yes' ? true : value === 'no' ? false : null };
}

function save() {
  const problem = declarationError(form.value);
  if (problem) {
    formError.value = problem;
    return;
  }
  formError.value = undefined;
  emit('save', {
    visibility: form.value.visibility as PostVisibility,
    madeForKids: form.value.madeForKids as boolean,
    planShort: form.value.plannedShort,
  });
}
</script>

<template>
  <form class="declaration" novalidate @submit.prevent="save">
    <h3 class="declaration__title">Veröffentlichung auf YouTube</h3>

    <AppSelect
      label="Sichtbarkeit"
      :options="visibilityOptions"
      :model-value="form.visibility || null"
      placeholder="Sichtbarkeit wählen"
      @update:model-value="(value: string) => (form = { ...form, visibility: value })"
    />

    <fieldset class="declaration__fieldset">
      <legend>Für Kinder bestimmt?</legend>
      <label class="declaration__option">
        <input type="radio" name="made-for-kids" value="yes" :checked="madeForKidsValue === 'yes'" @change="chooseMadeForKids('yes')" />
        Ja, das Video ist für Kinder bestimmt
      </label>
      <label class="declaration__option">
        <input type="radio" name="made-for-kids" value="no" :checked="madeForKidsValue === 'no'" @change="chooseMadeForKids('no')" />
        Nein, das Video ist nicht für Kinder bestimmt
      </label>
    </fieldset>

    <label class="declaration__option">
      <input v-model="form.plannedShort" type="checkbox" />
      Als Short planen (hochkant, höchstens drei Minuten)
    </label>

    <p v-if="formError || error" class="declaration__error" role="alert">{{ formError ?? error }}</p>

    <button type="submit" class="declaration__save" :disabled="busy">Angaben speichern</button>
  </form>
</template>

<style scoped>
.declaration {
  display: grid;
  gap: 10px;
  min-width: 0;
  font-size: 14px;
}

.declaration__title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}

.declaration__fieldset {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 8px;
}

.declaration__option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.declaration__error {
  margin: 0;
  color: var(--md-sys-color-error);
}

.declaration__save {
  justify-self: start;
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-primary);
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.declaration__save:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
