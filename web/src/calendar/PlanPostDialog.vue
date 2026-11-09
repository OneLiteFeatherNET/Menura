<script setup lang="ts">
import { ref, watch } from 'vue';
import AppDialog from '../components/ui/AppDialog.vue';
import AppSelect, { type SelectOption } from '../components/ui/AppSelect.vue';
import AppTextField from '../components/ui/AppTextField.vue';
import { EMPTY_PLAN_FORM, buildPlan, type PlanForm, type PlanSubmit } from './model';

// Plans a finished asset on one or more channels, or the next episodes of a series on one channel. The dialog checks the
// form and reports a request; the page sends it. Only editors and publishers reach this dialog, as on the server.
const props = withDefaults(
  defineProps<{
    open: boolean;
    assets: SelectOption[];
    channels: SelectOption[];
    series: SelectOption[];
    timeZone: string;
    busy: boolean;
    error?: string;
  }>(),
  { error: undefined },
);

const emit = defineEmits<{ close: []; submit: [plan: PlanSubmit] }>();

// Each opening starts from an empty form, with the asset chosen when there is only one.
function freshForm(): PlanForm {
  return { ...EMPTY_PLAN_FORM, assetId: props.assets.length === 1 ? (props.assets[0]?.value ?? '') : '' };
}

const form = ref<PlanForm>(freshForm());
const formError = ref<string | undefined>(undefined);

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    form.value = freshForm();
    formError.value = undefined;
  },
);

function patch(values: Partial<PlanForm>) {
  form.value = { ...form.value, ...values };
}

function submit() {
  const result = buildPlan(form.value, props.timeZone);
  if ('error' in result) {
    formError.value = result.error;
    return;
  }
  formError.value = undefined;
  emit('submit', result.submit);
}

function toggleChannel(id: string, checked: boolean) {
  const ids = form.value.channelIds.filter((channelId) => channelId !== id);
  patch({ channelIds: checked ? [...ids, id] : ids });
}
</script>

<template>
  <AppDialog :open="open" title="Beitrag planen" @close="emit('close')">
    <form class="plan-dialog" novalidate @submit.prevent="submit">
      <fieldset class="plan-dialog__mode">
        <legend>Art der Planung</legend>
        <label class="plan-dialog__option">
          <input type="radio" name="plan-mode" value="posts" :checked="form.mode === 'posts'" @change="patch({ mode: 'posts' })" />
          Einzelner Beitrag auf mehreren Kanälen
        </label>
        <label class="plan-dialog__option">
          <input type="radio" name="plan-mode" value="episodes" :checked="form.mode === 'episodes'" @change="patch({ mode: 'episodes' })" />
          Nächste Folgen einer Serie
        </label>
      </fieldset>

      <AppSelect
        label="Fertiger Clip"
        :options="assets"
        :model-value="form.assetId"
        placeholder="Clip wählen"
        @update:model-value="(value: string) => patch({ assetId: value })"
      />

      <template v-if="form.mode === 'posts'">
        <fieldset class="plan-dialog__channels">
          <legend>Kanäle</legend>
          <p v-if="channels.length === 0" class="plan-dialog__hint">
            Kein Kanal ist verbunden. Verbinden Sie zuerst einen Kanal.
          </p>
          <label v-for="channel in channels" :key="channel.value" class="plan-dialog__option">
            <input
              type="checkbox"
              :value="channel.value"
              :checked="form.channelIds.includes(channel.value)"
              @change="toggleChannel(channel.value, ($event.target as HTMLInputElement).checked)"
            />
            {{ channel.label }}
          </label>
        </fieldset>
        <AppTextField
          label="Titel"
          :model-value="form.title"
          :maxlength="200"
          @update:model-value="(value: string) => patch({ title: value })"
        />
        <div class="plan-dialog__time">
          <label class="plan-dialog__field">
            <span>Datum (Installationszeitzone)</span>
            <input type="date" :value="form.localDate" @input="patch({ localDate: ($event.target as HTMLInputElement).value })" />
          </label>
          <label class="plan-dialog__field">
            <span>Uhrzeit</span>
            <input type="time" :value="form.localTime" @input="patch({ localTime: ($event.target as HTMLInputElement).value })" />
          </label>
        </div>
      </template>

      <template v-else>
        <AppSelect
          label="Serie"
          :options="series"
          :model-value="form.seriesId"
          placeholder="Serie wählen"
          @update:model-value="(value: string) => patch({ seriesId: value })"
        />
        <AppSelect
          label="Kanal"
          :options="channels"
          :model-value="form.channelId"
          placeholder="Kanal wählen"
          @update:model-value="(value: string) => patch({ channelId: value })"
        />
        <label class="plan-dialog__field">
          <span>Anzahl Folgen (1 bis 10)</span>
          <input type="number" min="1" max="10" :value="form.count" @input="patch({ count: Number(($event.target as HTMLInputElement).value) })" />
        </label>
      </template>

      <p v-if="formError || error" class="plan-dialog__error" role="alert">{{ formError ?? error }}</p>

      <div class="plan-dialog__actions">
        <button type="button" class="plan-dialog__button" @click="emit('close')">Abbrechen</button>
        <button type="submit" class="plan-dialog__primary" :disabled="busy">
          {{ form.mode === 'posts' ? 'Entwurf anlegen' : 'Folgen planen' }}
        </button>
      </div>
    </form>
  </AppDialog>
</template>

<style scoped>
.plan-dialog {
  display: grid;
  gap: 14px;
  min-width: min(480px, 85vw);
  font-size: 14px;
}

.plan-dialog fieldset {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 8px;
}

.plan-dialog__option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.plan-dialog__hint {
  margin: 0;
}

.plan-dialog__time {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.plan-dialog__field {
  display: grid;
  gap: 4px;
}

.plan-dialog__field input {
  min-height: 40px;
  padding: 0 8px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 6px;
  font: inherit;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.plan-dialog__error {
  margin: 0;
  color: var(--md-sys-color-error);
}

.plan-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.plan-dialog__button,
.plan-dialog__primary {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  font: inherit;
  cursor: pointer;
}

.plan-dialog__button {
  border: 1px solid var(--md-sys-color-outline);
  background: transparent;
  color: var(--md-sys-color-primary);
}

.plan-dialog__primary {
  border: 1px solid var(--md-sys-color-primary);
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
}

.plan-dialog__primary:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
