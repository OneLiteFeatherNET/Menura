<script setup lang="ts">
import { computed } from 'vue';
import { formatTimestamp } from './format';
import { splitHighlight } from './highlight';
import { personName } from './rules';
import type { Person, TranscriptSegment } from './types';

const props = defineProps<{
  segments: readonly TranscriptSegment[];
  people: readonly Person[];
  query: string;
}>();

const rows = computed(() =>
  props.segments.map((segment) => ({
    key: `${segment.startSeconds}-${segment.personId ?? 'none'}`,
    time: formatTimestamp(segment.startSeconds),
    speaker: personName(props.people, segment.personId),
    parts: splitHighlight(segment.text, props.query),
  })),
);
</script>

<template>
  <ol class="transcript">
    <li v-for="row in rows" :key="row.key" class="transcript-line">
      <span class="transcript-time">{{ row.time }}</span>
      <span class="transcript-speaker">{{ row.speaker }}</span>
      <span class="transcript-text"><template v-for="(part, index) in row.parts" :key="index"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
    </li>
  </ol>
</template>

<style scoped>
.transcript {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.transcript-line {
  display: grid;
  grid-template-columns: 64px minmax(120px, max-content) 1fr;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border);
  align-items: baseline;
}

.transcript-time {
  font-variant-numeric: tabular-nums;
  color: var(--app-muted);
}

.transcript-speaker {
  font-size: 14px;
  color: var(--app-muted);
}

.transcript-text mark {
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
  border-radius: 4px;
  padding: 0 2px;
}

@media (max-width: 600px) {
  .transcript-line {
    grid-template-columns: 56px 1fr;
  }

  .transcript-text {
    grid-column: 1 / -1;
  }
}
</style>
