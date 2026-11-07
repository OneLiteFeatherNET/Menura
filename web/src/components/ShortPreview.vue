<script setup lang="ts">
import { computed } from 'vue';
import type { Cta, Series, SlotDefinition, Template } from '../api/types';
import { badgeColors } from '../domain/color';
import {
  CANVAS,
  isSlotVisible,
  previewRect,
  safeBands,
  SLOT_LABELS,
} from '../domain/slots';

// The short is drawn at a fraction of its design size (1080 x 1920). Text sizes are design pixels.
const props = withDefaults(
  defineProps<{
    template: Template;
    second: number;
    series: Series | null;
    cta: Cta;
    scale?: number;
  }>(),
  { scale: 0.4 },
);

const FONT_SIZE: Record<string, number> = {
  badge: 56,
  title: 92,
  captions: 44,
  logo: 36,
  cta: 44,
};

const SAMPLE_TITLE = 'Tetragon: Malware sicher ausführen?';
const SAMPLE_CAPTION = 'Pods heilen sich selbst, wenn ein Knoten ausfällt.';

const canvas = computed(() => ({
  width: CANVAS.width * props.scale,
  height: CANVAS.height * props.scale,
}));

const bands = computed(() => safeBands(props.template, props.scale));

const badgeStyle = computed(() =>
  badgeColors(props.series?.color ?? '#5C5870', props.series?.badgeTextColor ?? '#FFFFFF'),
);

function isVisible(slot: SlotDefinition): boolean {
  return isSlotVisible(slot, props.second);
}

function rectStyle(slot: SlotDefinition): Record<string, string> {
  const rect = previewRect(slot, props.scale);
  return {
    left: `${rect.left}px`,
    top: `${rect.top}px`,
    width: `${rect.width}px`,
    height: `${rect.height}px`,
  };
}

function fontStyle(name: string): Record<string, string> {
  return { fontSize: `${(FONT_SIZE[name] ?? 40) * props.scale}px` };
}

function ctaText(): string {
  return props.cta.de;
}
</script>

<template>
  <div
    class="short"
    role="img"
    :aria-label="`Vorschau des 9:16-Shorts bei Sekunde ${second}`"
    :style="{ width: `${canvas.width}px`, height: `${canvas.height}px` }"
  >
    <div
      class="zone"
      :style="{ top: `${bands.top.top}px`, height: `${bands.top.height}px` }"
    >
      <span class="zone-label">Obere Störzone {{ bands.top.percent }} %</span>
    </div>
    <div
      class="zone"
      :style="{ top: `${bands.bottom.top}px`, height: `${bands.bottom.height}px` }"
    >
      <span class="zone-label">Untere Störzone {{ bands.bottom.percent }} %</span>
    </div>

    <div
      v-for="slot in template.slots"
      :key="slot.name"
      class="slot"
      :class="{ 'slot-visible': isVisible(slot), 'slot-hidden': !isVisible(slot) }"
      :style="rectStyle(slot)"
      :data-slot="slot.name"
    >
      <span class="slot-label">{{ SLOT_LABELS[slot.name] ?? slot.name }}</span>

      <template v-if="isVisible(slot)">
        <span
          v-if="slot.name === 'badge'"
          class="badge-fill"
          :style="{ ...badgeStyle, ...fontStyle('badge') }"
        >{{ series?.badge.de ?? 'Serie' }}</span>
        <span v-else-if="slot.name === 'title'" class="text title" :style="fontStyle('title')">
          {{ SAMPLE_TITLE }}
        </span>
        <span v-else-if="slot.name === 'captions'" class="text" :style="fontStyle('captions')">
          {{ SAMPLE_CAPTION }}
        </span>
        <span v-else-if="slot.name === 'logo'" class="text logo" :style="fontStyle('logo')">Feder</span>
        <span v-else-if="slot.name === 'cta'" class="text cta" :style="fontStyle('cta')">
          {{ ctaText() }}
        </span>
        <span v-else-if="slot.name === 'session-tiles'" class="tiles" aria-hidden="true">
          <i></i><i></i><i></i>
        </span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.short {
  position: relative;
  overflow: hidden;
  flex: none;
  border-radius: 16px;
  background: #16141c;
  color: #ffffff;
  box-shadow: 0 0 0 1px var(--md-sys-color-outline-variant);
}

.zone {
  position: absolute;
  left: 0;
  right: 0;
  background: repeating-linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.16) 0 6px,
    rgba(255, 255, 255, 0.02) 6px 14px
  );
  pointer-events: none;
}

.zone-label {
  position: absolute;
  left: 8px;
  top: 6px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.7);
  color: #ffffff;
  font-size: 11px;
  line-height: 16px;
  white-space: nowrap;
}

.slot {
  position: absolute;
  overflow: hidden;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 120ms ease;
}

.slot-hidden {
  border: 1px dashed rgba(255, 255, 255, 0.28);
}

.slot-visible {
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: rgba(255, 255, 255, 0.06);
}

.slot-label {
  position: absolute;
  right: 4px;
  bottom: 2px;
  font-size: 10px;
  line-height: 14px;
  color: rgba(255, 255, 255, 0.7);
  white-space: nowrap;
}

.badge-fill {
  padding: 0 12px;
  border-radius: 8px;
  font-weight: 700;
  line-height: 1.1;
  white-space: nowrap;
}

.text {
  padding: 0 8px;
  text-align: center;
  font-weight: 600;
  line-height: 1.15;
}

.cta {
  font-weight: 700;
  background: rgba(0, 0, 0, 0.55);
  white-space: nowrap;
}

.logo {
  font-weight: 700;
  letter-spacing: 0.04em;
}

.tiles {
  display: flex;
  gap: 6px;
}

.tiles i {
  display: block;
  width: 28%;
  height: 70%;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.18);
}
</style>
