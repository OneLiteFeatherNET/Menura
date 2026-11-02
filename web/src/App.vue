<template>
  <div class="shell">
    <header class="app-bar">
      <h1 class="app-title">Menura</h1>
    </header>
    <md-divider></md-divider>
    <main class="content"></main>
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
  padding: 16px;
  background: var(--md-sys-color-surface-container);
}

.app-title {
  margin: 0;
  font-size: 22px;
  font-weight: 400;
  line-height: 28px;
}

.content {
  flex: 1;
  padding: 16px;
}
</style>
