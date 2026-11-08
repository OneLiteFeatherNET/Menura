<script setup lang="ts">
import { reactive, ref } from 'vue';
import { errorMessage } from '../../api/client';
import { poolApi } from './api';
import type { Asset, AssetKind, NewAsset, Person } from './types';

const props = defineProps<{
  open: boolean;
  people: readonly Person[];
}>();

const emit = defineEmits<{
  close: [];
  created: [asset: Asset];
}>();

const form = reactive({
  kind: 'raw' as AssetKind,
  title: '',
  tags: '',
  people: [] as string[],
  minutes: '60',
});

const saving = ref(false);
const formError = ref<string | null>(null);

function valueOf(event: Event): string {
  return (event.target as HTMLInputElement).value;
}

function togglePerson(personId: string, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked;
  form.people = checked
    ? [...form.people, personId]
    : form.people.filter((candidate) => candidate !== personId);
}

/** Tags are typed comma-separated; the server accepts them in lower case only. */
function parseTags(text: string): string[] {
  return text
    .split(',')
    .map((tag) => tag.trim().toLowerCase())
    .filter((tag) => tag !== '');
}

async function submit(): Promise<void> {
  formError.value = null;
  const minutes = Number(form.minutes);
  if (form.title.trim() === '' || !Number.isFinite(minutes) || minutes <= 0) {
    formError.value = 'Bitte Titel und eine Dauer über 0 Minuten angeben.';
    return;
  }

  const body: NewAsset = {
    kind: form.kind,
    title: form.title.trim(),
    tags: parseTags(form.tags),
    people: [...form.people],
    durationSeconds: Math.round(minutes * 60),
  };

  saving.value = true;
  try {
    const created = await poolApi.createAsset(body);
    Object.assign(form, { title: '', tags: '', people: [], minutes: '60' });
    emit('created', created);
    emit('close');
  } catch (cause) {
    formError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <!-- md-dialog takes its parts through light-DOM slot attributes; Vue's #slot syntax does not apply to custom elements. -->
  <!-- eslint-disable vue/no-deprecated-slot-attribute -->
  <md-dialog :open="props.open" class="pool-dialog" @close="emit('close')">
    <div slot="headline">Material hinzufügen</div>
    <form slot="content" class="form" @submit.prevent="submit">
      <md-outlined-select label="Art" :value="form.kind" @change="form.kind = valueOf($event) as AssetKind">
        <md-select-option value="raw" headline="Rohmaterial"></md-select-option>
        <md-select-option value="clip" headline="Clip"></md-select-option>
      </md-outlined-select>
      <md-outlined-text-field label="Titel" :value="form.title" required @input="form.title = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Tags (kommagetrennt)" :value="form.tags" @input="form.tags = valueOf($event)"></md-outlined-text-field>
      <md-outlined-text-field label="Dauer (Minuten)" type="number" min="1" :value="form.minutes" @input="form.minutes = valueOf($event)"></md-outlined-text-field>
      <fieldset class="people">
        <legend>Personen</legend>
        <label v-for="person in props.people" :key="person.id" class="person-option">
          <input type="checkbox" :value="person.id" :checked="form.people.includes(person.id)" @change="togglePerson(person.id, $event)" />
          <span>{{ person.name }}</span>
        </label>
      </fieldset>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
    </form>
    <div slot="actions">
      <md-text-button type="button" @click="emit('close')">Abbrechen</md-text-button>
      <md-filled-button type="button" :disabled="saving" @click="submit">Hinzufügen</md-filled-button>
    </div>
  </md-dialog>
  <!-- eslint-enable vue/no-deprecated-slot-attribute -->
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: min(420px, 80vw);
}

.people {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 8px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.person-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.form-error {
  margin: 0;
  color: var(--md-sys-color-error);
}
</style>
