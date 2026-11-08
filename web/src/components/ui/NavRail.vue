<script lang="ts">
export interface NavRailItem {
  path: string;
  label: string;
  /** SVG path data, drawn in a 24 by 24 box. The label is always there for the screen reader. */
  icon: string;
}
</script>

<script setup lang="ts">
// The navigation rail of the app. It shows the items it receives, in their order, and marks the one for the active
// path. A click reports the path as an event and leaves the navigation to the parent, so the rail needs no router.
// Below 600 px, and when `collapsed` is set, the labels are hidden and only the icons remain.
withDefaults(
  defineProps<{
    items: NavRailItem[];
    activePath: string;
    collapsed?: boolean;
  }>(),
  { collapsed: false },
);

const emit = defineEmits<{ navigate: [path: string] }>();

function onClick(event: MouseEvent, path: string) {
  // Modified or non-primary clicks keep the browser's behaviour, such as opening the link in a new tab.
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return;
  }
  event.preventDefault();
  emit('navigate', path);
}
</script>

<template>
  <nav class="nav-rail" :class="{ 'nav-rail--collapsed': collapsed }" aria-label="Hauptnavigation">
    <ul class="nav-rail__list">
      <li v-for="item in items" :key="item.path">
        <a
          class="nav-rail__item"
          :href="item.path"
          :aria-label="item.label"
          :aria-current="item.path === activePath ? 'page' : undefined"
          @click="onClick($event, item.path)"
        >
          <span class="nav-rail__indicator">
            <svg class="nav-rail__icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path :d="item.icon" fill="currentColor" />
            </svg>
          </span>
          <span class="nav-rail__label">{{ item.label }}</span>
        </a>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.nav-rail {
  box-sizing: border-box;
  flex: 0 0 auto;
  width: 96px;
  padding: 12px 8px;
  background: var(--md-sys-color-surface-container);
  font-family: Roboto, system-ui, sans-serif;
}

.nav-rail__list {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.nav-rail__item {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-height: 56px;
  padding: 4px 0;
  border-radius: 12px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 12px;
  line-height: 16px;
  text-align: center;
  text-decoration: none;
}

.nav-rail__indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 32px;
  border-radius: 16px;
}

.nav-rail__icon {
  width: 24px;
  height: 24px;
  fill: currentColor;
}

.nav-rail__item:hover {
  color: var(--md-sys-color-on-surface);
}

.nav-rail__item[aria-current='page'] {
  color: var(--md-sys-color-on-surface);
  font-weight: 500;
}

.nav-rail__item[aria-current='page'] .nav-rail__indicator {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.nav-rail__item:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}

/* Collapsed: the icons only, in a narrower rail. The same applies on narrow screens. */
.nav-rail--collapsed {
  width: 72px;
}

.nav-rail--collapsed .nav-rail__label {
  display: none;
}

@media (max-width: 599px) {
  .nav-rail {
    width: 72px;
    padding: 8px 4px;
  }

  .nav-rail__label {
    display: none;
  }
}
</style>
