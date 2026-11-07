<script setup lang="ts">
import { useRoute } from 'vue-router';
import { LATER_PHASES, NAV_GROUPS } from '../router';

const route = useRoute();

function headingId(groupId: string): string {
  return `rail-group-${groupId}`;
}

// Prefix match for sub-pages (e.g. /brand-kit/render-bundle), exact match for the overview.
// Routes are siblings, so router record matching alone would not mark the parent page active.
function isActive(to: string): boolean {
  if (to === '/') return route.path === '/';
  return route.path === to || route.path.startsWith(`${to}/`);
}
</script>

<template>
  <nav class="rail" aria-label="Hauptnavigation">
    <div class="rail-header">
      <p class="rail-brand">Menura</p>
      <p class="rail-subline">Content-Hub · OneLiteFeather</p>
    </div>

    <p class="rail-phase">Phase 1 · Wiedererkennung &amp; Messbarkeit</p>

    <section v-for="group in NAV_GROUPS" :key="group.id" class="rail-group">
      <h2 :id="headingId(group.id)" class="rail-group-title">{{ group.title }}</h2>
      <ul class="rail-list" :aria-labelledby="headingId(group.id)">
        <li v-for="item in group.items" :key="item.to">
          <RouterLink v-slot="{ href, navigate }" :to="item.to" custom>
            <a
              :href="href"
              class="rail-item"
              :class="{ 'is-active': isActive(item.to) }"
              :aria-current="isActive(item.to) ? 'page' : undefined"
              @click="navigate"
            >
              {{ item.label }}
            </a>
          </RouterLink>
        </li>
      </ul>
    </section>

    <section class="rail-group">
      <h2 :id="headingId('roadmap')" class="rail-group-title">Roadmap</h2>
      <ul class="rail-list" :aria-labelledby="headingId('roadmap')">
        <li v-for="item in LATER_PHASES" :key="item.to">
          <RouterLink :to="item.to" class="rail-item is-later" active-class="is-active" exact-active-class="is-active">
            <span>{{ item.label }}</span>
            <span class="phase-chip">Phase {{ item.phase }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>
  </nav>
</template>

<style scoped>
.rail {
  position: sticky;
  top: 0;
  align-self: flex-start;
  max-height: 100vh;
  overflow-y: auto;
  width: 240px;
  flex: none;
  box-sizing: border-box;
  padding: 16px 12px;
  background: var(--md-sys-color-surface);
  border-right: 1px solid var(--md-sys-color-outline-variant);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rail-header {
  padding: 0 16px;
}

.rail-brand {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  line-height: 32px;
  color: var(--md-sys-color-on-surface);
}

.rail-subline {
  margin: 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--md-sys-color-on-surface-variant);
}

.rail-phase {
  margin: 0;
  padding: 0 16px;
  font-size: 12px;
  line-height: 16px;
  letter-spacing: 0.5px;
  color: var(--md-sys-color-primary);
}

.rail-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rail-group-title {
  margin: 0 0 0 16px;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
}

.rail-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 48px;
  box-sizing: border-box;
  padding: 0 16px;
  border-radius: 24px;
  font-size: 17px;
  line-height: 24px;
  color: var(--md-sys-color-on-surface-variant);
  text-decoration: none;
}

.rail-item:hover {
  background: color-mix(in srgb, var(--md-sys-color-on-surface) 8%, transparent);
}

.rail-item.is-active {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  font-weight: 500;
}

.rail-item.is-later {
  opacity: 0.6;
}

.rail-item.is-later.is-active {
  opacity: 1;
}

.phase-chip {
  font-size: 12px;
  line-height: 16px;
  padding: 2px 8px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 8px;
  white-space: nowrap;
}
</style>
