<script setup lang="ts">
import type { Session } from '../api/types';
import { signOut } from '../auth/session';

defineProps<{
  session: Session;
}>();
</script>

<template>
  <header class="top-bar">
    <span class="prototype-chip">Prototyp · Demo-Daten</span>
    <div class="spacer"></div>
    <ul class="roles" aria-label="Deine Rollen">
      <li v-for="role in session.roles" :key="role" class="role-chip">{{ role }}</li>
    </ul>
    <md-text-button class="sign-out" @click="signOut">Abmelden</md-text-button>
  </header>
</template>

<style scoped>
.top-bar {
  height: 64px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  background: var(--md-sys-color-surface-variant);
  color: var(--md-sys-color-on-surface-variant);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
}

.prototype-chip {
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  padding: 4px 12px;
  border-radius: 8px;
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
}

.spacer {
  flex: 1;
}

.roles {
  display: flex;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.role-chip {
  font-size: 14px;
  line-height: 20px;
  padding: 4px 12px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  color: var(--md-sys-color-on-surface-variant);
}
</style>
