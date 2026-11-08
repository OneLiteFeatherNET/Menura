<script setup lang="ts">
import { computed } from 'vue';
import type { CalendarItem, DryRunProblem, PostRevision } from '../api/posts';
import MetaList from '../components/ui/MetaList.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import PostRevisions from './PostRevisions.vue';
import {
  CHANNEL_STATE_LABELS,
  OUTCOME_LABELS,
  STATUS_LABELS,
  STEP_LABELS,
  STEP_STATE_LABELS,
  VISIBILITY_LABELS,
  dayLabel,
  type PostActions,
  type ScheduleRefusal,
} from './model';

// One post of the calendar: its status, the dry-run problems, the publication steps and attempts, and the actions the
// person may take. The actions come from the role and the status (postActions); the server checks them again. A refused
// schedule shows the problems of the refusal next to the action.
const props = defineProps<{
  item: CalendarItem;
  actions: PostActions;
  busy: boolean;
  refusal: ScheduleRefusal | null;
  revisions: PostRevision[];
  revisionsStatus: 'loading' | 'error' | 'ready';
  notice?: string;
}>();

const emit = defineEmits<{
  close: [];
  submit: [];
  approve: [];
  schedule: [];
  reconnect: [];
  accept: [revisionId: string];
  reject: [revisionId: string];
  'retry-revisions': [];
}>();

const SEVERITY_LABELS = { error: 'Fehler', warning: 'Hinweis' } as const;

const facts = computed(() => [
  { label: 'Kanal', value: `${props.item.channelName} (${props.item.platform})` },
  { label: 'Kanalstatus', value: CHANNEL_STATE_LABELS[props.item.channelState] },
  { label: 'Termin', value: `${dayLabel(props.item.localDate)}, ${props.item.localTime} Uhr` },
  { label: 'Sichtbarkeit', value: VISIBILITY_LABELS[props.item.visibility] },
  { label: 'Versuche', value: String(props.item.attempts) },
]);

// The problems of a refusal replace the stored ones only when the refusal lists some; a failure without a list keeps them.
const shownProblems = computed<DryRunProblem[]>(() =>
  props.refusal && props.refusal.problems.length > 0 ? props.refusal.problems : props.item.problems,
);
const needsReconnect = computed(
  () => props.item.channelState === 'needs_reconnection' || shownProblems.value.some((problem) => problem.action === 'reconnect'),
);
const hasAnyAction = computed(() => props.actions.submit || props.actions.approve || props.actions.schedule);
</script>

<template>
  <section class="post-panel" aria-labelledby="post-panel-title" :data-status="item.status">
    <header class="post-panel__header">
      <div class="post-panel__heading">
        <h2 id="post-panel-title" class="post-panel__title">{{ item.title }}</h2>
        <StatusBadge :label="STATUS_LABELS[item.status]" :tone="item.status === 'failed' ? 'error' : 'info'" />
      </div>
      <button type="button" class="post-panel__close" @click="emit('close')">Schließen</button>
    </header>

    <p v-if="notice" class="post-panel__notice" role="status">{{ notice }}</p>

    <MetaList :items="facts" />

    <p v-if="item.url" class="post-panel__link">
      <a :href="item.url" target="_blank" rel="noopener noreferrer">Beitrag auf der Plattform öffnen</a>
    </p>

    <div v-if="item.failureReason" class="post-panel__alert" role="alert">
      Die Veröffentlichung ist fehlgeschlagen: {{ item.failureReason }}
    </div>

    <div v-if="refusal" class="post-panel__alert" role="alert">
      <p class="post-panel__alert-text">{{ refusal.message }}</p>
    </div>

    <section class="post-panel__section" aria-labelledby="post-panel-problems">
      <h3 id="post-panel-problems" class="post-panel__section-title">Probleme der Terminprüfung</h3>
      <p v-if="shownProblems.length === 0" class="post-panel__muted">Keine Probleme gefunden.</p>
      <ul v-else class="post-panel__list">
        <li v-for="problem in shownProblems" :key="problem.code" class="post-panel__problem" :data-severity="problem.severity">
          <StatusBadge
            :label="SEVERITY_LABELS[problem.severity]"
            :tone="problem.severity === 'error' ? 'error' : 'warning'"
          />
          <span>{{ problem.message }}</span>
        </li>
      </ul>
      <button v-if="needsReconnect" type="button" class="post-panel__button" :disabled="busy" @click="emit('reconnect')">
        Kanal neu verbinden
      </button>
    </section>

    <section class="post-panel__section" aria-labelledby="post-panel-steps">
      <h3 id="post-panel-steps" class="post-panel__section-title">Schritte</h3>
      <p v-if="item.steps.length === 0" class="post-panel__muted">Noch keine Schritte.</p>
      <ul v-else class="post-panel__list">
        <li v-for="step in item.steps" :key="step.step">
          {{ STEP_LABELS[step.step] }}: {{ STEP_STATE_LABELS[step.state] }}
          <span v-if="step.reason" class="post-panel__muted">({{ step.reason }})</span>
        </li>
      </ul>
    </section>

    <section class="post-panel__section" aria-labelledby="post-panel-attempts">
      <h3 id="post-panel-attempts" class="post-panel__section-title">Veröffentlichungsversuche</h3>
      <p v-if="item.attemptHistory.length === 0" class="post-panel__muted">Noch kein Versuch.</p>
      <ol v-else class="post-panel__list">
        <li v-for="attempt in item.attemptHistory" :key="attempt.attempt">
          Versuch {{ attempt.attempt }}:
          {{ attempt.outcome ? OUTCOME_LABELS[attempt.outcome] : 'ohne Ergebnis' }}
          <span v-if="attempt.reason" class="post-panel__muted">({{ attempt.reason }})</span>
        </li>
      </ol>
    </section>

    <div v-if="hasAnyAction" class="post-panel__actions">
      <button v-if="actions.submit" type="button" class="post-panel__button" :disabled="busy" @click="emit('submit')">
        Zur Prüfung senden
      </button>
      <button v-if="actions.approve" type="button" class="post-panel__button" :disabled="busy" @click="emit('approve')">
        Freigeben
      </button>
      <button v-if="actions.schedule" type="button" class="post-panel__button" :disabled="busy" @click="emit('schedule')">
        Einplanen
      </button>
    </div>

    <PostRevisions
      v-if="actions.decideRevisions || revisions.length > 0"
      :revisions="revisions"
      :status="revisionsStatus"
      :can-decide="actions.decideRevisions"
      :busy="busy"
      @accept="(id: string) => emit('accept', id)"
      @reject="(id: string) => emit('reject', id)"
      @retry="emit('retry-revisions')"
    />
  </section>
</template>

<style scoped>
.post-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 12px;
  background: var(--md-sys-color-surface-container-low);
  min-width: 0;
  font-size: 14px;
}

.post-panel__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.post-panel__heading {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.post-panel__title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.post-panel__section {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.post-panel__section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}

.post-panel__list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  overflow-wrap: anywhere;
}

.post-panel__list:has(.post-panel__problem) {
  padding-left: 0;
  list-style: none;
}

.post-panel__problem {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.post-panel__muted {
  margin: 0;
  color: var(--md-sys-color-on-surface-variant);
}

.post-panel__notice,
.post-panel__link p {
  margin: 0;
}

.post-panel__alert {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
  overflow-wrap: anywhere;
}

.post-panel__alert-text {
  margin: 0;
}

.post-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-panel__button,
.post-panel__close {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 999px;
  background: transparent;
  color: var(--md-sys-color-primary);
  font: inherit;
  cursor: pointer;
}

.post-panel__button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
