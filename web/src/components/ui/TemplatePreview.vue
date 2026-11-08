<script setup lang="ts">
import { computed } from 'vue';
import { slotBox, type Template } from '../../brand/brandKit';

// Draws a layout template with sample parameters at its own frame size. Slots are placed in percent of the frame and
// coloured with the brand tokens of the surrounding element (--brand-<name>), so the preview shows the kit's look.
// The optional safe-zone guide marks the bands the badge, title, captions and logo must keep out of.
const props = withDefaults(
  defineProps<{
    template: Template;
    values: Record<string, string>;
    label: string;
    showSafeZone?: boolean;
  }>(),
  { showSafeZone: false },
);

const aspect = computed(() => `${props.template.width} / ${props.template.height}`);

function slotText(slot: Template['slots'][number]): string {
  if (slot.kind !== 'text') {
    return slot.kind === 'tiles' ? 'Sitzungen' : '';
  }
  if (slot.parameter) {
    return props.values[slot.parameter] ?? `[${slot.parameter}]`;
  }
  return slot.name;
}

function slotStyle(slot: Template['slots'][number]): Record<string, string> {
  const style = slotBox(slot);
  if (slot.background) {
    style.background = `var(--brand-${slot.background})`;
  }
  if (slot.colour) {
    style.color = `var(--brand-${slot.colour})`;
  }
  return style;
}
</script>

<template>
  <div class="template-preview" role="group" :aria-label="label" :style="{ aspectRatio: aspect }">
    <div
      v-for="slot in template.slots"
      :key="slot.name"
      class="template-preview__slot"
      :class="`template-preview__slot--${slot.kind}`"
      :data-slot="slot.name"
      :style="slotStyle(slot)"
    >
      {{ slotText(slot) }}
    </div>
    <template v-if="showSafeZone">
      <div class="template-preview__band template-preview__band--top" aria-hidden="true" data-testid="safe-zone-top"></div>
      <div class="template-preview__band template-preview__band--bottom" aria-hidden="true" data-testid="safe-zone-bottom"></div>
    </template>
  </div>
</template>

<style scoped>
.template-preview {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  max-width: 320px;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--brand-surface, var(--md-sys-color-surface));
  font-family: Roboto, system-ui, sans-serif;
}

.template-preview__slot {
  position: absolute;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  padding: 2%;
  min-width: 0;
  overflow: hidden;
  font-size: clamp(10px, 2.2vw, 16px);
  line-height: 1.2;
  overflow-wrap: anywhere;
  color: var(--brand-on-surface, var(--md-sys-color-on-surface));
}

.template-preview__slot--shape {
  padding: 0;
}

.template-preview__slot--image {
  color: var(--md-sys-color-on-surface-variant);
  outline: 1px dashed var(--md-sys-color-outline);
  outline-offset: -1px;
  justify-content: center;
}

.template-preview__band {
  position: absolute;
  left: 0;
  right: 0;
  pointer-events: none;
  background: repeating-linear-gradient(45deg, transparent 0 6px, rgb(255 0 0 / 0.12) 6px 12px);
}

.template-preview__band--top {
  top: 0;
  height: 14%;
}

.template-preview__band--bottom {
  top: 65%;
  bottom: 0;
}
</style>
