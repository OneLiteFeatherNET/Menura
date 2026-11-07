<script setup lang="ts">
import type { Session } from '../api/types';
import NavRail from './NavRail.vue';
import TopBar from './TopBar.vue';

defineProps<{
  session: Session;
}>();
</script>

<template>
  <div class="shell">
    <TopBar :session="session" />
    <div class="body">
      <NavRail />
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.content {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 32px 40px 56px;
}
</style>
