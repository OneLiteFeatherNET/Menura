<script setup lang="ts">
import { computed } from 'vue';
import { berlinTime } from './dates';
import { platformLabel, statusLabel } from './labels';
import type { Publication } from './types';

const props = defineProps<{
  publication: Publication;
  colour?: string;
  selected?: boolean;
}>();

defineEmits<{
  select: [publication: Publication];
}>();

const time = computed(() => berlinTime(new Date(props.publication.publishAt)));
</script>

<template>
  <button
    type="button"
    class="entry"
    data-entry
    :class="[`status-${publication.status}`, { selected }]"
    :style="{ '--stripe': colour ?? 'var(--md-sys-color-outline)' }"
    :aria-label="`${time} ${publication.metadata.title}, ${platformLabel(publication.platform)}, ${statusLabel(publication.status)}`"
    @click="$emit('select', publication)"
  >
    <span class="entry-time">{{ time }}</span>
    <span class="entry-title">{{ publication.metadata.title }}</span>
    <span class="entry-meta">
      <span class="entry-platform">{{ platformLabel(publication.platform) }}</span>
      <span class="entry-status">{{ statusLabel(publication.status) }}</span>
    </span>
  </button>
</template>

<style scoped>
.entry {
  display: grid;
  gap: 2px;
  width: 100%;
  min-width: 0;
  padding: 4px 6px 4px 8px;
  border: 1px solid var(--app-border);
  border-left: 4px solid var(--stripe);
  border-radius: 8px;
  background: var(--app-card);
  color: inherit;
  font: inherit;
  font-size: 12px;
  line-height: 16px;
  text-align: left;
  cursor: pointer;
}

.entry.selected {
  outline: 2px solid var(--md-sys-color-primary);
}

.entry-time {
  font-weight: 600;
}

.entry-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  color: var(--app-muted);
}

.entry-status {
  font-weight: 600;
}

.status-failed .entry-status {
  color: var(--md-sys-color-error);
}

.status-published .entry-status {
  color: var(--md-sys-color-primary);
}
</style>
