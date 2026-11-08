<script setup lang="ts">
// Attaches a transcript to one raw item of the pool. The file is sent to the transcript route; a refused file shows
// the line the server named.
import { ref, watch } from 'vue';
import { uploadTranscript } from '../../api/libraryUploads';
import AppDialog from '../../components/ui/AppDialog.vue';
import { isAbortError } from './concurrency';
import { failureMessage } from './uploadRunner';
import TranscriptUpload, { type TranscriptPhase } from './TranscriptUpload.vue';
import { transcriptFormatOf } from './transcript';

const props = defineProps<{
  open: boolean;
  rawItemId: string | null;
  itemTitle: string;
}>();
const emit = defineEmits<{ close: []; uploaded: [] }>();

const phase = ref<TranscriptPhase>('idle');
const error = ref<string | null>(null);
const segmentCount = ref<number | null>(null);
let controller: AbortController | null = null;

watch(
  () => props.open,
  (open) => {
    if (open) return;
    controller?.abort();
    phase.value = 'idle';
    error.value = null;
    segmentCount.value = null;
  },
);

async function upload(file: File) {
  const format = transcriptFormatOf(file.name);
  if (!props.rawItemId) return;
  if (!format) {
    error.value = 'Bitte eine WebVTT-Datei (.vtt) oder eine SRT-Datei (.srt) wählen.';
    return;
  }
  error.value = null;
  phase.value = 'uploading';
  controller = new AbortController();
  try {
    const view = await uploadTranscript(props.rawItemId, file, format, controller.signal);
    segmentCount.value = view.segmentCount;
    phase.value = 'done';
    emit('uploaded');
  } catch (failure) {
    if (isAbortError(failure)) return;
    phase.value = 'idle';
    error.value = failureMessage(failure);
  }
}
</script>

<template>
  <AppDialog :open="open" title="Transkript" @close="emit('close')">
    <TranscriptUpload :item-title="itemTitle" :phase="phase" :error="error" :segment-count="segmentCount" @upload="upload" />
  </AppDialog>
</template>
