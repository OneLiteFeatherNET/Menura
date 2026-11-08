<script setup lang="ts">
import { computed } from 'vue';
import type { PostRevision } from '../api/posts';
import LoadState from '../components/ui/LoadState.vue';
import { REVISION_STATUS_LABELS } from './model';

// The revisions proposed for one post, oldest first. A revision names only the fields it changes; the post changes only
// when a publisher accepts it, so only a publisher sees the decision buttons, and only for a pending revision.
const props = defineProps<{
  revisions: PostRevision[];
  status: 'loading' | 'error' | 'ready';
  canDecide: boolean;
  busy?: boolean;
}>();

const emit = defineEmits<{ accept: [revisionId: string]; reject: [revisionId: string]; retry: [] }>();

const changes = (revision: PostRevision) => {
  const fields: string[] = [];
  if (revision.title !== null) fields.push('Titel');
  if (revision.description !== null) fields.push('Text');
  if (revision.hashtags !== null) fields.push('Hashtags');
  if (revision.coverAssetId !== null) fields.push('Titelbild');
  if (revision.scheduledAt !== null) fields.push('Termin');
  if (revision.channelId !== null) fields.push('Kanal');
  return fields.length > 0 ? fields.join(', ') : 'Keine Änderung';
};

const pending = computed(() => props.revisions.filter((revision) => revision.status === 'pending'));
</script>

<template>
  <section class="post-revisions" aria-labelledby="post-revisions-title">
    <h3 id="post-revisions-title" class="post-revisions__title">Vorschläge</h3>
    <LoadState :status="status" error-message="Die Vorschläge konnten nicht geladen werden." @retry="emit('retry')">
      <p v-if="revisions.length === 0" class="post-revisions__empty">Zu diesem Beitrag gibt es keine Vorschläge.</p>
      <ol v-else class="post-revisions__list">
        <li v-for="revision in revisions" :key="revision.id" class="post-revisions__item" :data-status="revision.status">
          <p class="post-revisions__head">
            <span class="post-revisions__status">{{ REVISION_STATUS_LABELS[revision.status] }}</span>
            <span class="post-revisions__changes">{{ changes(revision) }}</span>
          </p>
          <p class="post-revisions__rationale">{{ revision.rationale }}</p>
          <div v-if="canDecide && revision.status === 'pending'" class="post-revisions__actions">
            <button type="button" class="post-revisions__button" :disabled="busy" @click="emit('accept', revision.id)">
              Annehmen
            </button>
            <button type="button" class="post-revisions__button" :disabled="busy" @click="emit('reject', revision.id)">
              Ablehnen
            </button>
          </div>
        </li>
      </ol>
      <p v-if="pending.length > 0 && !canDecide" class="post-revisions__hint">
        Offene Vorschläge werden von einer veröffentlichenden Person entschieden.
      </p>
    </LoadState>
  </section>
</template>

<style scoped>
.post-revisions {
  display: grid;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
}

.post-revisions__title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.post-revisions__empty,
.post-revisions__hint {
  margin: 0;
}

.post-revisions__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.post-revisions__item {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 8px;
  min-width: 0;
}

.post-revisions__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
}

.post-revisions__status {
  font-weight: 500;
}

.post-revisions__changes,
.post-revisions__rationale {
  margin: 0;
  overflow-wrap: anywhere;
}

.post-revisions__actions {
  display: flex;
  gap: 8px;
}

.post-revisions__button {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.post-revisions__button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
