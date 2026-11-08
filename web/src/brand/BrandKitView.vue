<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import CardGrid from '../components/ui/CardGrid.vue';
import ColourSwatch from '../components/ui/ColourSwatch.vue';
import LoadState from '../components/ui/LoadState.vue';
import MetaList from '../components/ui/MetaList.vue';
import PageHeader from '../components/ui/PageHeader.vue';
import TemplatePreview from '../components/ui/TemplatePreview.vue';
import AppSelect from '../components/ui/AppSelect.vue';
import { parameterProblems, sampleValues, tokenStyle, type BrandKitVersion } from './brandKit';

// The brand kit page as a view: it shows one version and never fetches. The page that loads the version passes it in.
const props = withDefaults(
  defineProps<{
    status: 'loading' | 'error' | 'ready';
    version?: BrandKitVersion | null;
    errorMessage?: string;
  }>(),
  { version: null, errorMessage: 'Das Markenkit konnte nicht geladen werden.' },
);

const emit = defineEmits<{ retry: [] }>();

const selectedFormat = ref<string | null>(null);

const kit = computed(() => props.version?.kit ?? null);

const templateOptions = computed(() =>
  (kit.value?.templates ?? []).map((template) => ({ value: template.format, label: template.name })),
);

const selectedTemplate = computed(() => {
  const templates = kit.value?.templates ?? [];
  return templates.find((template) => template.format === selectedFormat.value) ?? templates[0] ?? null;
});

const values = computed(() => (selectedTemplate.value ? sampleValues(selectedTemplate.value) : {}));

const problems = computed(() => (selectedTemplate.value ? parameterProblems(selectedTemplate.value, values.value) : []));

const meta = computed(() =>
  props.version
    ? [
        { label: 'Version', value: String(props.version.version) },
        { label: 'Veröffentlicht', value: new Date(props.version.createdAt).toLocaleDateString('de-DE') },
        { label: 'Farben', value: String(kit.value?.colours.length ?? 0) },
        { label: 'Vorlagen', value: String(kit.value?.templates.length ?? 0) },
      ]
    : [],
);

// Whenever a new version arrives, the preview falls back to its first template.
watch(
  () => props.version,
  () => {
    selectedFormat.value = null;
  },
);

const status = computed(() => (props.status === 'ready' && !props.version ? 'empty' : props.status));
</script>

<template>
  <section class="brand-kit" aria-label="Markenkit" :style="kit ? tokenStyle(kit, 'light') : undefined">
    <PageHeader title="Markenkit" :subtitle="version ? `Version ${version.version}` : undefined" />
    <LoadState :status="status" :errorMessage="errorMessage" emptyText="Noch kein Markenkit veröffentlicht." @retry="emit('retry')">
      <div class="brand-kit__body">
        <MetaList :items="meta" />

        <section class="brand-kit__section" aria-labelledby="brand-kit-colours">
          <h3 id="brand-kit-colours" class="brand-kit__heading">Farben</h3>
          <CardGrid label="Farbtokens" emptyText="Keine Farben.">
            <li v-for="token in kit?.colours ?? []" :key="token.name">
              <ColourSwatch :name="token.name" :light="token.light" :dark="token.dark" />
            </li>
          </CardGrid>
        </section>

        <section class="brand-kit__section" aria-labelledby="brand-kit-marks">
          <h3 id="brand-kit-marks" class="brand-kit__heading">Logos und Marken</h3>
          <CardGrid label="Marken" emptyText="Keine Marken.">
            <li v-for="mark in kit?.marks ?? []" :key="mark.name">
              <p class="brand-kit__mark-name">{{ mark.name }}</p>
              <MetaList
                :items="mark.variants.map((variant) => ({ label: variant.name, value: variant.file }))"
              />
            </li>
          </CardGrid>
        </section>

        <section class="brand-kit__section" aria-labelledby="brand-kit-templates">
          <h3 id="brand-kit-templates" class="brand-kit__heading">Vorlagen</h3>
          <AppSelect
            label="Format"
            :options="templateOptions"
            :modelValue="selectedTemplate?.format ?? null"
            @update:modelValue="selectedFormat = $event"
          />
          <TemplatePreview
            v-if="selectedTemplate"
            :template="selectedTemplate"
            :values="values"
            :label="`Vorschau ${selectedTemplate.name} mit Beispielwerten`"
            :showSafeZone="selectedTemplate.format === 'short-9x16'"
          />
          <p v-if="problems.length" class="brand-kit__problems" role="alert">{{ problems.join(' ') }}</p>
        </section>
      </div>
    </LoadState>
  </section>
</template>

<style scoped>
.brand-kit {
  display: grid;
  gap: 24px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.brand-kit__body {
  display: grid;
  gap: 24px;
  min-width: 0;
}

.brand-kit__section {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.brand-kit__heading {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.brand-kit__mark-name {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
}

.brand-kit__problems {
  margin: 0;
  color: var(--md-sys-color-error);
  font-size: 14px;
}
</style>
