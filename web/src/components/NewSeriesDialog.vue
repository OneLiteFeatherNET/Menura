<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { api, errorMessage } from '../api/client';
import type { Audience, ColorToken, NewSeries, Series } from '../api/types';
import { audienceLabel, weekdayLabel } from '../domain/format';

const props = defineProps<{
  open: boolean;
  colorTokens: readonly ColorToken[];
}>();

const emit = defineEmits<{
  close: [];
  created: [series: Series];
}>();

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const AUDIENCES: Audience[] = ['players', 'developers', 'mixed'];

// Series colours are colour tokens of the brand kit; the series-* tokens come first.
const selectableTokens = computed(() => {
  const series = props.colorTokens.filter((token) => token.name.startsWith('series-'));
  return series.length > 0 ? series : props.colorTokens;
});

const form = reactive({
  name: '',
  badgeDe: '',
  badgeEn: '',
  colorToken: '',
  badgeTextColor: '#FFFFFF',
  openingFormula: 'Kurz erklärt:',
  hashtag: '#',
  audience: 'developers' as Audience,
  weekday: 'TUESDAY',
  slot: '18:00',
});

// Keep a valid colour token selected, starting with the first one the brand kit offers.
watch(
  selectableTokens,
  (tokens) => {
    if (!tokens.some((token) => token.name === form.colorToken)) {
      form.colorToken = tokens[0]?.name ?? '';
    }
  },
  { immediate: true },
);

const saving = ref(false);
const formError = ref<string | null>(null);

function valueOf(event: Event): string {
  return (event.target as HTMLInputElement).value;
}

function selectedColor(): string {
  const token = selectableTokens.value.find((candidate) => candidate.name === form.colorToken);
  return token?.light ?? '#000000';
}

async function submit(): Promise<void> {
  formError.value = null;
  if (form.name.trim() === '' || form.badgeDe.trim() === '' || form.hashtag.trim() === '') {
    formError.value = 'Bitte Name, Badge-Text und Hashtag ausfüllen.';
    return;
  }

  const body: NewSeries = {
    name: form.name.trim(),
    badge: { de: form.badgeDe.trim(), en: (form.badgeEn.trim() || form.badgeDe).trim() },
    colorToken: form.colorToken,
    color: selectedColor(),
    badgeTextColor: form.badgeTextColor.trim(),
    openingFormula: form.openingFormula.trim(),
    hashtag: form.hashtag.trim(),
    audience: form.audience,
    weekday: form.weekday,
    slot: form.slot.trim(),
    playlists: {},
    active: true,
  };

  saving.value = true;
  try {
    const created = await api.createSeries(body);
    Object.assign(form, { name: '', badgeDe: '', badgeEn: '', hashtag: '#' });
    emit('created', created);
    emit('close');
  } catch (cause) {
    formError.value = errorMessage(cause, { serverDetailOn422: true });
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <!-- md-dialog takes its parts through light-DOM slot attributes; Vue's #slot syntax does not apply to custom elements. -->
  <!-- eslint-disable vue/no-deprecated-slot-attribute -->
  <md-dialog :open="props.open" class="series-dialog" @close="emit('close')">
    <div slot="headline">Neue Serie</div>
    <form slot="content" class="form" @submit.prevent="submit">
      <md-outlined-text-field label="Name" :value="form.name" required @input="form.name = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Badge-Text (Deutsch)" :value="form.badgeDe" required @input="form.badgeDe = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Badge-Text (Englisch)" :value="form.badgeEn" @input="form.badgeEn = valueOf($event)"></md-outlined-text-field>
      <md-outlined-select label="Farbe (Token)" :value="form.colorToken" @change="form.colorToken = valueOf($event)">
        <md-select-option
          v-for="token in selectableTokens"
          :key="token.name"
          :value="token.name"
          :headline="token.name"
        ></md-select-option>
      </md-outlined-select>
      <md-outlined-text-field label="Schrift auf Badge (Hex)" :value="form.badgeTextColor" @input="form.badgeTextColor = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Einleitung" :value="form.openingFormula" @input="form.openingFormula = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Hashtag" :value="form.hashtag" required @input="form.hashtag = valueOf($event)"></md-outlined-text-field>
      <md-outlined-select label="Zielgruppe" :value="form.audience" @change="form.audience = valueOf($event) as Audience">
        <md-select-option
          v-for="audience in AUDIENCES"
          :key="audience"
          :value="audience"
          :headline="audienceLabel(audience)"
        ></md-select-option>
      </md-outlined-select>
      <md-outlined-select label="Wochentag" :value="form.weekday" @change="form.weekday = valueOf($event)">
        <md-select-option
          v-for="day in WEEKDAYS"
          :key="day"
          :value="day"
          :headline="weekdayLabel(day)"
        ></md-select-option>
      </md-outlined-select>
      <md-outlined-text-field label="Uhrzeit" :value="form.slot" @input="form.slot = valueOf($event)"></md-outlined-text-field>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
    </form>
    <div slot="actions">
      <md-text-button type="button" @click="emit('close')">Abbrechen</md-text-button>
      <md-filled-button type="button" :disabled="saving" @click="submit">Speichern</md-filled-button>
    </div>
  </md-dialog>
  <!-- eslint-enable vue/no-deprecated-slot-attribute -->
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 420px;
}

.form-error {
  margin: 0;
  color: var(--md-sys-color-error);
}
</style>
