<script setup lang="ts">
defineProps<{
  title: string;
  phase: number;
  sentence: string;
}>();
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>{{ title }}</h2>
        <p class="page-lead">{{ sentence }}</p>
      </div>
      <span class="chip">Phase {{ phase }} · noch nicht gebaut</span>
    </header>
    <section class="card placeholder">
      <p class="muted">Dieser Bereich ist im Prototyp nur als Platzhalter angelegt.</p>
    </section>
  </div>
</template>
