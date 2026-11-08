<script setup lang="ts">
import { RouterLink } from 'vue-router';
import PageHeader from '../components/ui/PageHeader.vue';
</script>

<template>
  <section class="not-found">
    <PageHeader title="Seite nicht gefunden" subtitle="Diese Adresse führt zu keinem Bereich von Menura." />
    <p class="not-found__back"><RouterLink to="/">Zur Übersicht</RouterLink></p>
  </section>
</template>

<style scoped>
.not-found {
  display: grid;
  gap: 16px;
  font-family: Roboto, system-ui, sans-serif;
}

.not-found__back {
  margin: 0;
  font-size: 14px;
}

.not-found__back a {
  color: var(--md-sys-color-primary);
}
</style>
