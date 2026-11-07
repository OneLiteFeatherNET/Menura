<script setup lang="ts">
import { LATER_PHASES, PRIMARY_NAV } from '../router';
</script>

<template>
  <nav class="rail" aria-label="Hauptnavigation">
    <ul class="rail-list">
      <li v-for="item in PRIMARY_NAV" :key="item.to">
        <RouterLink :to="item.to" class="rail-item" active-class="is-active" exact-active-class="is-active">
          {{ item.label }}
        </RouterLink>
      </li>
    </ul>

    <md-divider></md-divider>

    <p class="rail-caption">Später</p>
    <ul class="rail-list">
      <li v-for="item in LATER_PHASES" :key="item.to">
        <RouterLink :to="item.to" class="rail-item is-later" active-class="is-active" exact-active-class="is-active">
          <span>{{ item.label }}</span>
          <span class="phase">Phase {{ item.phase }}</span>
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.rail {
  width: 240px;
  flex: none;
  box-sizing: border-box;
  padding: 16px 12px;
  background: var(--md-sys-color-surface);
  border-right: 1px solid var(--md-sys-color-outline-variant);
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.phase {
  font-size: 13px;
  white-space: nowrap;
}

.rail-caption {
  margin: 8px 16px 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
