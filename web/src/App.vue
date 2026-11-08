<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchSession, roleLabel, signInUrl, signOut, type SessionInfo } from './auth/session';
import LoadState from './components/ui/LoadState.vue';
import BrandKitPage from './brand/BrandKitPage.vue';
import PoolPage from './library/PoolPage.vue';
import ChannelsPage from './features/channels/ChannelsPage.vue';

// The shell shows who is signed in and with which role. The navigation lists the features a signed-in person can open.
const status = ref<'loading' | 'error' | 'ready'>('loading');
const session = ref<SessionInfo | null>(null);
const onChannelsPage = computed(() => window.location.pathname.replace(/\/+$/, '') === '/channels');

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
    <nav v-if="session" class="nav" aria-label="Hauptnavigation">
      <a class="nav__link" href="/channels" :aria-current="onChannelsPage ? 'page' : undefined">Kanäle</a>
    </nav>
    <main class="content">
      <ChannelsPage v-if="session && onChannelsPage" :roles="session.roles" />
      <template v-else-if="session">
        <BrandKitPage />
        <PoolPage />
      </template>
      <p v-else-if="!session && status === 'ready' && onChannelsPage" class="content__hint">
        Melden Sie sich an, um die Kanäle zu verwalten.
      </p>
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

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px;
}

.nav__link {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  padding: 0 16px;
  border-radius: 999px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 14px;
  text-decoration: none;
}

.nav__link[aria-current='page'] {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.nav__link:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}

.content {
  flex: 1;
  padding: 16px;
}

.content__hint {
  margin: 0;
  font-size: 14px;
}
</style>
