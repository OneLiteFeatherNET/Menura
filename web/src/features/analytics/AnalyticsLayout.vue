<script setup lang="ts">
const ANALYTICS_TABS = [
  { to: '/analytics', label: 'Überblick' },
  { to: '/analytics/kanaele', label: 'Kanäle' },
  { to: '/analytics/uhrzeit', label: 'Uhrzeit & Wochentag' },
  { to: '/analytics/serien', label: 'Serien' },
  { to: '/analytics/join-attribution', label: 'Join-Attribution' },
  { to: '/analytics/datenpunkte', label: 'Datenpunkte' },
];
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Analytics</h2>
        <p class="page-lead">Messwerte aller Kanäle an einer Stelle.</p>
      </div>
    </header>

    <nav class="tabs" aria-label="Analytics-Ansichten">
      <RouterLink
        v-for="tab in ANALYTICS_TABS"
        :key="tab.to"
        :to="tab.to"
        class="tab"
        exact-active-class="is-active"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>

    <RouterView />
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.tab {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  box-sizing: border-box;
  padding: 0 16px;
  border-radius: 20px;
  border: 1px solid var(--md-sys-color-outline-variant);
  font-size: 15px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  text-decoration: none;
}

.tab:hover {
  background: color-mix(in srgb, var(--md-sys-color-on-surface) 8%, transparent);
}

.tab.is-active {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  border-color: transparent;
  font-weight: 500;
}
</style>
