<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { roleLabel } from '../auth/session';
import PageHeader from '../components/ui/PageHeader.vue';

// The start page: a greeting for the signed-in person and links to the areas their roles can open. The app shell
// passes the areas, so this page holds no list of its own.
const props = defineProps<{
  roles: string[];
  areas: readonly { path: string; label: string }[];
}>();

const roleNames = computed(() => props.roles.map(roleLabel).join(', '));
</script>

<template>
  <section class="overview">
    <PageHeader title="Übersicht" subtitle="Willkommen bei Menura. Wählen Sie einen Bereich." />
    <p class="overview__role">
      Ihre Rolle: <span data-testid="overview-role">{{ roleNames || 'keine Rolle' }}</span>
    </p>
    <ul v-if="areas.length" class="overview__links">
      <li v-for="area in areas" :key="area.path">
        <RouterLink class="overview__link" :to="area.path">{{ area.label }}</RouterLink>
      </li>
    </ul>
    <p v-else class="overview__empty">Für Ihre Rolle ist noch kein Bereich freigegeben.</p>
  </section>
</template>

<style scoped>
.overview {
  display: grid;
  gap: 16px;
  font-family: Roboto, system-ui, sans-serif;
}

.overview__role,
.overview__empty {
  margin: 0;
  font-size: 14px;
}

.overview__links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.overview__link {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  color: var(--md-sys-color-primary);
  font-size: 14px;
  text-decoration: none;
}

.overview__link:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
