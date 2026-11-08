<script setup lang="ts">
import { ref } from 'vue';
import AppSelect, { type SelectOption } from '../ui/AppSelect.vue';
import AppTextField from '../ui/AppTextField.vue';
import {
  AUDIENCE_LABELS,
  POSTING_SLOTS,
  WEEKDAY_LABELS,
  type Audience,
  type SeriesDraft,
} from '../../api/series';

// The form to create a series. The colour and template choices are the brand kit's own names. Only presence is checked
// here; the contrast, the token names and the hashtag rules are checked by the server, whose refusal is shown by the page.
const props = withDefaults(
  defineProps<{
    colourTokens: string[];
    templates: string[];
    busy?: boolean;
    error?: string;
  }>(),
  { busy: false, error: undefined },
);

const emit = defineEmits<{ submit: [draft: SeriesDraft] }>();

function pick(options: string[], preferred: string): string {
  return options.includes(preferred) ? preferred : (options[0] ?? '');
}

const name = ref('');
const badgeDe = ref('');
const openingFormula = ref('Kurz erklärt:');
const hashtag = ref('');
const colourToken = ref(pick(props.colourTokens, 'primary'));
const badgeToken = ref(pick(props.colourTokens, 'on-primary'));
const layoutTemplate = ref(pick(props.templates, 'short-9x16'));
const audience = ref<Audience>('developers');
const weekday = ref('1');
const postingSlot = ref('18');
const errors = ref<Record<string, string>>({});

const colourOptions = (): SelectOption[] => props.colourTokens.map((token) => ({ value: token, label: token }));
const templateOptions = (): SelectOption[] => props.templates.map((format) => ({ value: format, label: format }));
const audienceOptions: SelectOption[] = (Object.keys(AUDIENCE_LABELS) as Audience[]).map((value) => ({
  value,
  label: AUDIENCE_LABELS[value],
}));
const weekdayOptions: SelectOption[] = WEEKDAY_LABELS.map((label, index) => ({ value: String(index + 1), label }));
const slotOptions: SelectOption[] = POSTING_SLOTS.map((hour) => ({ value: String(hour), label: `${hour} Uhr` }));

function check(): boolean {
  const next: Record<string, string> = {};
  if (!name.value.trim()) next.name = 'Der Name ist erforderlich.';
  if (!badgeDe.value.trim()) next.badge = 'Der Badge-Text ist erforderlich.';
  if (!openingFormula.value.trim()) next.openingFormula = 'Die Eröffnungsformel ist erforderlich.';
  if (!/^#[\p{L}\p{N}_]{2,49}$/u.test(hashtag.value.trim())) {
    next.hashtag = 'Der Hashtag beginnt mit # und hat 2 bis 49 Buchstaben, Ziffern oder Unterstriche.';
  }
  errors.value = next;
  return Object.keys(next).length === 0;
}

function submit() {
  if (!check()) return;
  emit('submit', {
    name: name.value.trim(),
    badgeText: { de: badgeDe.value.trim() },
    colourToken: colourToken.value,
    badgeToken: badgeToken.value,
    openingFormula: openingFormula.value.trim(),
    layoutTemplate: layoutTemplate.value,
    hashtag: hashtag.value.trim(),
    audience: audience.value,
    weekday: Number(weekday.value),
    postingSlot: Number(postingSlot.value),
    active: true,
  });
}
</script>

<template>
  <form class="series-form" aria-label="Neue Serie" novalidate @submit.prevent="submit">
    <AppTextField v-model="name" label="Name" :error="errors.name" :maxlength="80" />
    <AppTextField v-model="badgeDe" label="Badge-Text (Deutsch)" :error="errors.badge" :maxlength="40" />
    <AppTextField v-model="openingFormula" label="Eröffnungsformel" :error="errors.openingFormula" :maxlength="120" />
    <AppTextField v-model="hashtag" label="Hashtag" :error="errors.hashtag" :maxlength="50" />
    <AppSelect v-model="colourToken" label="Serienfarbe (Marken-Token)" :options="colourOptions()" />
    <AppSelect v-model="badgeToken" label="Badge-Textfarbe (Marken-Token)" :options="colourOptions()" />
    <AppSelect v-model="layoutTemplate" label="Layout-Vorlage" :options="templateOptions()" />
    <AppSelect v-model="audience" label="Zielgruppe" :options="audienceOptions" />
    <AppSelect v-model="weekday" label="Bevorzugter Wochentag" :options="weekdayOptions" />
    <AppSelect v-model="postingSlot" label="Bevorzugter Termin" :options="slotOptions" />

    <p v-if="error" class="series-form__error" role="alert">{{ error }}</p>
    <button type="submit" class="series-form__submit" :disabled="busy">Serie anlegen</button>
  </form>
</template>

<style scoped>
.series-form {
  display: grid;
  gap: 12px;
  max-width: 560px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.series-form__error {
  margin: 0;
  color: var(--md-sys-color-error);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.series-form__submit {
  justify-self: start;
  min-height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.series-form__submit:disabled {
  opacity: 0.6;
  cursor: progress;
}
</style>
