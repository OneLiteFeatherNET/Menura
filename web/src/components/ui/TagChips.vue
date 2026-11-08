<script setup lang="ts">
// Viewers see the tags only. Editors get a remove button per tag, named after the tag so that each is distinct.
withDefaults(
  defineProps<{
    tags: string[];
    removable?: boolean;
    removeLabel?: string;
    emptyText?: string;
  }>(),
  { removable: false, removeLabel: 'Entfernen', emptyText: undefined },
);

const emit = defineEmits<{ remove: [tag: string] }>();
</script>

<template>
  <div class="tag-chips">
    <ul v-if="tags.length" class="tag-chips__list">
      <li v-for="tag in tags" :key="tag" class="tag-chips__chip">
        <span class="tag-chips__text">{{ tag }}</span>
        <button
          v-if="removable"
          type="button"
          class="tag-chips__remove"
          :aria-label="`${removeLabel}: ${tag}`"
          @click="emit('remove', tag)"
        >
          ×
        </button>
      </li>
    </ul>
    <p v-else-if="emptyText" class="tag-chips__empty">{{ emptyText }}</p>
  </div>
</template>

<style scoped>
.tag-chips {
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.tag-chips__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.tag-chips__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  font-size: 14px;
}

.tag-chips__text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.tag-chips__remove {
  flex: none;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.tag-chips__remove:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
}

.tag-chips__empty {
  margin: 0;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
