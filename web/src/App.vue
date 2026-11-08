<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchSession, roleLabel, signInUrl, signOut, type SessionInfo } from './auth/session';
import LoadState from './components/ui/LoadState.vue';
import BrandKitPage from './brand/BrandKitPage.vue';

// The shell shows who is signed in and with which role. Feature navigation is added by the feature changes.
const status = ref<'loading' | 'error' | 'ready'>('loading');
const session = ref<SessionInfo | null>(null);

async function load() {
  status.value = 'loading';
  try {
    session.value = await fetchSession();
    status.value = 'ready';
  } catch {
    status.value = 'error';
  }
}

async function endSession() {
  await signOut();
  session.value = null;
}

onMounted(load);
</script>

<template>
  <div class="shell">
    <header class="app-bar">
      <h1 class="app-title">Menura</h1>
      <div class="session">
        <LoadState :status="status" errorMessage="Die Anmeldung konnte nicht geprüft werden." @retry="load">
          <template v-if="session">
            <span class="session__role" data-testid="session-role">{{ session.roles.map(roleLabel).join(', ') }}</span>
            <button type="button" class="session__button" @click="endSession">Abmelden</button>
          </template>
          <a v-else class="session__button" :href="signInUrl()">Anmelden</a>
        </LoadState>
      </div>
    </header>
    <md-divider></md-divider>
    <main class="content">
      <BrandKitPage v-if="session" />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
}

.app-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  background: var(--md-sys-color-surface-container);
}

.app-title {
  margin: 0;
  font-size: 22px;
  font-weight: 400;
  line-height: 28px;
}

.session {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.session__role {
  font-size: 14px;
}

.session__button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  text-decoration: none;
  cursor: pointer;
}

.content {
  flex: 1;
  padding: 16px;
}
</style>
