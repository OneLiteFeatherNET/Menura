<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import { fetchSession, roleLabel, signInUrl, signOut, type SessionInfo } from './auth/session';
import LoadState from './components/ui/LoadState.vue';
import NavRail from './components/ui/NavRail.vue';
import ChannelsPage from './features/channels/ChannelsPage.vue';
import SeriesPage from './components/series/SeriesPage.vue';
import { canOpen, findArea, normalizePath, visibleAreas } from './navigation/areas';
import OverviewPage from './navigation/OverviewPage.vue';

// The shell shows who is signed in and with which role. The navigation lists the areas that role may open; the
// areas themselves are routed in router.ts.
const router = useRouter();
const route = useRoute();
const status = ref<'loading' | 'error' | 'ready'>('loading');
const session = ref<SessionInfo | null>(null);

const roles = computed(() => session.value?.roles ?? []);
const railItems = computed(() => visibleAreas(roles.value));
const currentPath = computed(() => normalizePath(route.path));
// An area that exists but is not open to this role is not shown; a direct link to it gets a notice instead.
const blocked = computed(() => {
  const area = findArea(route.path);
  return area !== undefined && !canOpen(area, roles.value);
});
// The start page lists the areas that can be opened from it, that is every area but itself.
const overviewAreas = computed(() => railItems.value.filter((area) => area.path !== '/'));

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

function navigate(path: string) {
  void router.push(path);
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
    <div class="body">
      <NavRail v-if="session" :items="railItems" :active-path="currentPath" @navigate="navigate" />
      <main class="content">
        <p v-if="!session && status === 'ready'" class="content__hint">
          Melden Sie sich an, um Menura zu nutzen.
        </p>
        <p v-else-if="session && blocked" class="content__hint">
          Für diesen Bereich hat Ihre Rolle keinen Zugriff.
        </p>
        <RouterView v-else-if="session" v-slot="{ Component }">
          <!-- The pages that show per-role content take the roles as a prop; every other page is mounted as it is. -->
          <OverviewPage v-if="Component.type === OverviewPage" :roles="session.roles" :areas="overviewAreas" />
          <ChannelsPage v-else-if="Component.type === ChannelsPage" :roles="session.roles" />
          <SeriesPage v-else-if="Component.type === SeriesPage" :roles="session.roles" />
          <component :is="Component" v-else />
        </RouterView>
      </main>
    </div>
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

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.content {
  flex: 1;
  min-width: 0;
  padding: 16px;
}

.content__hint {
  margin: 0;
  font-size: 14px;
}
</style>
