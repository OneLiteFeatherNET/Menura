<script setup lang="ts">
// The upload dialog of the pool. It declares the file, runs the upload sequence and shows its state. An unfinished
// upload of this browser is found again on opening (its session id is remembered here as a convenience only); the
// editor picks the same file to continue, and the composite checksum proves that it is the same file.
import { computed, ref, watch } from 'vue';
import {
  cancelAssetUpload,
  completeAssetUpload,
  confirmDuplicateAssetUpload,
  putPart,
  readAssetUpload,
  startAssetUpload,
  type PartUrl,
  type UploadSessionView,
} from '../../api/libraryUploads';
import AppDialog from '../../components/ui/AppDialog.vue';
import { checkDeclaration, type DeclarationField, type UploadMode } from './declaration';
import { readMediaFacts } from './fileMedia';
import { isAbortError } from './concurrency';
import { ApiProblem } from './problems';
import { forgetUpload, recallUpload, rememberUpload, type ResumeStorage } from './resume';
import {
  continueUpload,
  failureMessage,
  resumeUpload,
  startUpload,
  type Prepared,
  type UploadApi,
  type UploadSource,
} from './uploadRunner';
import { IDLE_UPLOAD, reduceUpload, type UploadEvent, type UploadState } from './uploadState';
import UploadForm, { type UploadFormValues } from './UploadForm.vue';
import UploadStatus from './UploadStatus.vue';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; completed: [] }>();

const EMPTY_VALUES: UploadFormValues = { durationSeconds: '', width: '', height: '', streamTitle: '', streamDate: '' };

const mode = ref<UploadMode>('finished');
const file = ref<File | null>(null);
const values = ref<UploadFormValues>({ ...EMPTY_VALUES });
const submitted = ref(false);
const upload = ref<UploadState>(IDLE_UPLOAD);
const unfinished = ref<{ id: string; view: UploadSessionView } | null>(null);
const resumeError = ref<string | null>(null);

// The state of the running sequence, kept outside the reactive state: the digests and the file are large or not
// serialisable, and only the dialog reads them.
let controller: AbortController | null = null;
let prepared: Prepared | null = null;
let source: UploadSource | null = null;

const api: UploadApi = {
  start: (body) => startAssetUpload(body),
  read: (id) => readAssetUpload(id),
  confirmDuplicate: (id) => confirmDuplicateAssetUpload(id),
  complete: (id) => completeAssetUpload(id),
  cancel: (id) => cancelAssetUpload(id),
  putPart: (url, body, digest, signal) => putPart(url, body, digest, signal),
};

const check = computed(() =>
  file.value
    ? checkDeclaration({
        mode: mode.value,
        fileName: file.value.name,
        sizeBytes: file.value.size,
        ...values.value,
      })
    : null,
);

const errors = computed<Partial<Record<DeclarationField, string>>>(() => {
  if (!submitted.value || !check.value || check.value.ok) return {};
  return check.value.errors;
});

function dispatch(event: UploadEvent) {
  upload.value = reduceUpload(upload.value, event);
}

function browserStorage(): ResumeStorage {
  return {
    getItem: (key) => window.localStorage.getItem(key),
    setItem: (key, value) => window.localStorage.setItem(key, value),
    removeItem: (key) => window.localStorage.removeItem(key),
  };
}

function sourceOf(picked: File): UploadSource {
  return {
    sizeBytes: picked.size,
    bytes: async (start, end) => new Uint8Array(await picked.slice(start, end).arrayBuffer()),
    blob: (start, end) => picked.slice(start, end),
  };
}

function reset() {
  controller?.abort();
  controller = null;
  prepared = null;
  source = null;
  file.value = null;
  values.value = { ...EMPTY_VALUES };
  submitted.value = false;
  mode.value = 'finished';
  upload.value = IDLE_UPLOAD;
  resumeError.value = null;
}

// Each opening looks for an unfinished session of this browser. A session that is no longer uploading is forgotten.
watch(
  () => props.open,
  async (open) => {
    if (!open) {
      reset();
      return;
    }
    const id = recallUpload(browserStorage());
    unfinished.value = null;
    if (!id) return;
    try {
      const view = await readAssetUpload(id);
      if (view.state === 'uploading') {
        unfinished.value = { id, view };
      } else {
        forgetUpload(browserStorage());
      }
    } catch (error) {
      if (error instanceof ApiProblem && error.problem.status === 404) {
        forgetUpload(browserStorage());
      }
    }
  },
);

watch(
  () => upload.value.phase,
  (phase) => {
    if (phase === 'completed') {
      forgetUpload(browserStorage());
      emit('completed');
    }
  },
);

async function onPick(picked: File) {
  file.value = picked;
  submitted.value = false;
  const facts = await readMediaFacts(picked);
  if (file.value !== picked) return;
  // The facts fill only the fields the editor has not typed in.
  values.value = {
    ...values.value,
    durationSeconds: values.value.durationSeconds || (facts.durationSeconds?.toString() ?? ''),
    width: values.value.width || (facts.width?.toString() ?? ''),
    height: values.value.height || (facts.height?.toString() ?? ''),
  };
}

function handleError(error: unknown) {
  if (isAbortError(error)) return; // stopped on purpose: the session stays, the editor can resume it
  dispatch({ type: 'failed', message: failureMessage(error) });
}

async function transfer(parts: readonly PartUrl[]) {
  const sessionId = upload.value.sessionId;
  if (!sessionId || !prepared || !source) return;
  try {
    await continueUpload({
      api,
      source,
      prepared,
      sessionId,
      parts,
      dispatch,
      signal: controller?.signal,
    });
  } catch (error) {
    handleError(error);
  }
}

async function start() {
  submitted.value = true;
  if (!file.value || !check.value || !check.value.ok) return;
  const picked = file.value;
  const fields = check.value.fields;
  controller = new AbortController();
  source = sourceOf(picked);
  try {
    const started = await startUpload({
      api,
      source,
      fields,
      fileName: picked.name,
      dispatch,
      remember: (id) => rememberUpload(browserStorage(), id),
    });
    prepared = started.prepared;
    if (started.view.state === 'flagged') return;
    await transfer(started.view.parts);
  } catch (error) {
    handleError(error);
  }
}

async function confirmDuplicate() {
  const sessionId = upload.value.sessionId;
  if (!sessionId) return;
  controller = new AbortController();
  try {
    const view = await confirmDuplicateAssetUpload(sessionId);
    dispatch({ type: 'duplicateConfirmed' });
    await transfer(view.parts);
  } catch (error) {
    handleError(error);
  }
}

async function retry() {
  const sessionId = upload.value.sessionId;
  if (!sessionId || !prepared || !source) return;
  controller = new AbortController();
  dispatch({ type: 'retrying' });
  try {
    const view = await readAssetUpload(sessionId);
    await transfer(view.parts);
  } catch (error) {
    handleError(error);
  }
}

async function cancel() {
  controller?.abort();
  const sessionId = upload.value.sessionId;
  if (sessionId) {
    try {
      await cancelAssetUpload(sessionId);
    } catch {
      dispatch({ type: 'failed', message: 'Der Abbruch konnte nicht gesendet werden. Bitte erneut versuchen.' });
      return;
    }
  }
  forgetUpload(browserStorage());
  prepared = null;
  dispatch({ type: 'cancelled' });
}

async function onResumeFile(event: Event) {
  const picked = (event.target as HTMLInputElement).files?.[0];
  const session = unfinished.value;
  if (!picked || !session) return;
  resumeError.value = null;
  controller = new AbortController();
  source = sourceOf(picked);
  dispatch({ type: 'selected', fileName: picked.name, partCount: session.view.partCount });
  try {
    const result = await resumeUpload(api, session.id, source, (done, total) => dispatch({ type: 'hashed', done, total }));
    if ('mismatch' in result) {
      dispatch({ type: 'reset' });
      source = null;
      resumeError.value = 'Das ist nicht dieselbe Datei wie bei der unterbrochenen Übertragung. Bitte die Datei wählen, die hochgeladen wurde.';
      return;
    }
    prepared = result.prepared;
    unfinished.value = null;
    dispatch({
      type: 'sessionStarted',
      sessionId: session.id,
      flagged: false,
      duplicateOf: null,
      confirmedParts: result.view.confirmedParts,
    });
    await transfer(result.view.parts);
  } catch (error) {
    handleError(error);
  }
}

function onDialogClose() {
  emit('close');
}
</script>

<template>
  <AppDialog :open="open" title="Hochladen" @close="onDialogClose">
    <div class="upload-dialog">
      <section v-if="unfinished && upload.phase === 'idle'" class="upload-dialog__resume" aria-label="Unterbrochene Übertragung">
        <p class="upload-dialog__text">
          Eine Übertragung ist unterbrochen: {{ unfinished.view.confirmedParts.length }} von {{ unfinished.view.partCount }}
          Teilen sind im Speicher. Wählen Sie dieselbe Datei, um sie fortzusetzen.
        </p>
        <input type="file" aria-label="Datei der unterbrochenen Übertragung" accept="video/*,image/*" @change="onResumeFile" />
        <p v-if="resumeError" class="upload-dialog__error" role="alert">{{ resumeError }}</p>
      </section>

      <UploadForm
        v-if="upload.phase === 'idle'"
        v-model:mode="mode"
        :file-name="file?.name ?? ''"
        :values="values"
        :errors="errors"
        @update:values="values = $event"
        @pick="onPick"
      />
      <UploadStatus
        v-else
        :state="upload"
        @retry="retry"
        @cancel="cancel"
        @confirm-duplicate="confirmDuplicate"
        @close="onDialogClose"
      />
    </div>

    <template #actions>
      <button
        v-if="upload.phase === 'idle'"
        type="button"
        class="upload-dialog__button upload-dialog__button--primary"
        :disabled="!file"
        @click="start"
      >
        Hochladen
      </button>
    </template>
  </AppDialog>
</template>

<style scoped>
.upload-dialog {
  display: grid;
  gap: 16px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.upload-dialog__resume {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: var(--md-sys-color-surface-variant);
}

.upload-dialog__text,
.upload-dialog__error {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.upload-dialog__error {
  color: var(--md-sys-color-error);
}

.upload-dialog__button {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  font: inherit;
  cursor: pointer;
}

.upload-dialog__button:disabled {
  opacity: 0.38;
  cursor: default;
}

.upload-dialog__button:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
