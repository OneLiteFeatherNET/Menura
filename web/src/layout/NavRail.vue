<script setup lang="ts">
import { useRoute } from 'vue-router';
import { NAV_ITEMS } from '../router';

const route = useRoute();

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

    <ul class="rail-list">
      <li v-for="item in NAV_ITEMS" :key="item.to">
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
</style>
