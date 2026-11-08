<script setup lang="ts">
// One colour token of the brand kit: its name, and its value for the light and for the dark role. The chips carry the
// value as text, so the hex codes are readable without colour.
defineProps<{
  name: string;
  light: string;
  dark: string;
}>();
</script>

<template>
  <div class="colour-swatch">
    <p class="colour-swatch__name">{{ name }}</p>
    <div class="colour-swatch__roles">
      <div class="colour-swatch__role">
        <span class="colour-swatch__chip" :style="{ background: light }" aria-hidden="true"></span>
        <span class="colour-swatch__value">Hell {{ light }}</span>
      </div>
      <div class="colour-swatch__role">
        <span class="colour-swatch__chip" :style="{ background: dark }" aria-hidden="true"></span>
        <span class="colour-swatch__value">Dunkel {{ dark }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.colour-swatch {
  display: grid;
  gap: 8px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
}

.colour-swatch__name {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.colour-swatch__roles {
  display: grid;
  gap: 4px;
}

.colour-swatch__role {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.colour-swatch__chip {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid var(--md-sys-color-outline-variant);
}

.colour-swatch__value {
  font-size: 12px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}
</style>
