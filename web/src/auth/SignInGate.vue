<script setup lang="ts">
import type { SessionState } from './session';
import { signOut } from './session';

// Everything the gate shows before the app shell: sign-in, missing role, or an error.
defineProps<{
  state: SessionState;
}>();

function retry(): void {
  window.location.reload();
}
</script>

<template>
  <main class="gate">
    <section class="gate-card">
      <h1 class="gate-title">Menura</h1>

      <template v-if="state.status === 'loading'">
        <md-circular-progress indeterminate></md-circular-progress>
        <p class="gate-text">Anmeldung wird geprüft …</p>
      </template>

      <template v-else-if="state.status === 'anonymous'">
        <p class="gate-text">
          Der Prototyp zeigt Kanal- und Serienzahlen. Melde dich mit deinem Microsoft-Konto an,
          um fortzufahren.
        </p>
        <md-filled-button href="/auth/login">Mit Microsoft anmelden</md-filled-button>
      </template>

      <template v-else-if="state.status === 'forbidden'">
        <h2 class="gate-subtitle">Kein Zugriff – dir fehlt eine Menura-Rolle</h2>
        <p class="gate-text">Wende dich an einen Menura-Admin, damit er dir eine Rolle zuweist.</p>
        <md-outlined-button @click="signOut">Abmelden</md-outlined-button>
      </template>

      <template v-else-if="state.status === 'error'">
        <p class="gate-text" role="alert">{{ state.message }}</p>
        <md-filled-tonal-button @click="retry">Erneut versuchen</md-filled-tonal-button>
      </template>
    </section>
  </main>
</template>

<style scoped>
.gate {
  min-height: 100vh;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--md-sys-color-surface);
}

.gate-card {
  width: 100%;
  max-width: 440px;
  box-sizing: border-box;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, var(--md-sys-color-surface));
  border: 1px solid var(--md-sys-color-outline-variant);
}

.gate-title {
  margin: 0;
  font-size: 32px;
  line-height: 40px;
  font-weight: 400;
}

.gate-subtitle {
  margin: 0;
  font-size: 22px;
  line-height: 28px;
  font-weight: 500;
  color: var(--md-sys-color-error);
}

.gate-text {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
