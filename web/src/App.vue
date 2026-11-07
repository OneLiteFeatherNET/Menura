<script setup lang="ts">
import { computed, onMounted, provide, ref } from 'vue';
import SignInGate from './auth/SignInGate.vue';
import { loadSession, SESSION_KEY, type SessionState } from './auth/session';
import AppShell from './layout/AppShell.vue';

// The sign-in gate decides what the user sees: the shell only with a signed-in session that has a role.
const state = ref<SessionState>({ status: 'loading' });
const session = computed(() => (state.value.status === 'signed-in' ? state.value.session : null));
provide(SESSION_KEY, session);

onMounted(async () => {
  state.value = await loadSession();
});
</script>

<template>
  <AppShell v-if="session" :session="session" />
  <SignInGate v-else :state="state" />
</template>
