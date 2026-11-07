<script setup lang="ts">
// Shows progress or an error in place of the page content; the default slot shows the content.
defineProps<{
  loading: boolean;
  error: string | null;
}>();
</script>

<template>
  <div v-if="loading" class="load-state" role="status">
    <md-circular-progress indeterminate></md-circular-progress>
    <span>Lädt …</span>
  </div>
  <div v-else-if="error" class="card card-error" role="alert">{{ error }}</div>
  <slot v-else />
</template>
