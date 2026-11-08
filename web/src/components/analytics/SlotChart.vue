<script setup lang="ts">
import { computed } from 'vue';
import type { EqualAgeBy, EqualAgeSlot } from '../../api/analytics';
import { NOT_AVAILABLE, barFraction, formatNumber, maxValue, slotLabel } from '../../analytics/format';

// One column per slot (an hour or a weekday). The column height is the median of the metric; the number under the
// column is the count of posts in that slot. A null median has no column height and reads 'nicht verfügbar'. The chart
// only draws the data it gets; it never fetches.
const props = withDefaults(
  defineProps<{
    caption: string;
    by: EqualAgeBy;
    slots: EqualAgeSlot[];
    emptyText?: string;
  }>(),
  { emptyText: 'Keine Daten.' },
);

const max = computed(() => maxValue(props.slots.map((slot) => slot.median)));
// Posts exist, but none of them has the metric: the platform does not provide it for these posts.
const allMissing = computed(
  () => props.slots.some((slot) => slot.posts > 0) && props.slots.every((slot) => slot.median === null),
);

function medianText(slot: EqualAgeSlot): string {
  return slot.median === null ? NOT_AVAILABLE : formatNumber(slot.median);
}

// The columns are hidden from assistive technology; the image's name lists every slot with its median and post count.
const summary = computed(
  () =>
    `${props.caption}: ${props.slots
      .map((slot) => `${slotLabel(props.by, slot.slot)} ${medianText(slot)}, ${slot.posts} Beiträge`)
      .join('; ')}`,
);
</script>

<template>
  <p v-if="slots.length === 0" class="slot-chart__empty">{{ emptyText }}</p>
  <figure v-else class="slot-chart">
    <figcaption class="slot-chart__caption">{{ caption }}</figcaption>
    <p v-if="allMissing" class="slot-chart__missing">Die Plattform liefert diese Kennzahl für die Beiträge nicht.</p>
    <div class="slot-chart__scroll">
      <div class="slot-chart__plot" role="img" :aria-label="summary">
        <div v-for="slot in slots" :key="slot.slot" class="slot-chart__column" aria-hidden="true">
          <span class="slot-chart__value" :class="{ 'slot-chart__value--missing': slot.median === null }">
            {{ slot.median === null ? '–' : formatNumber(slot.median) }}
          </span>
          <span class="slot-chart__area">
            <span
              v-if="slot.median !== null"
              class="slot-chart__bar"
              :style="{ height: `${barFraction(slot.median, max) * 100}%` }"
            ></span>
          </span>
          <span class="slot-chart__slot">{{ slotLabel(by, slot.slot) }}</span>
          <span class="slot-chart__posts">{{ slot.posts }}</span>
        </div>
      </div>
    </div>
    <p class="slot-chart__legend">Zahl unter der Spalte: Beiträge in diesem Zeitfenster.</p>
  </figure>
</template>

<style scoped>
.slot-chart {
  display: grid;
  gap: 8px;
  margin: 0;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.slot-chart__caption,
.slot-chart__empty,
.slot-chart__legend,
.slot-chart__missing {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.slot-chart__scroll {
  max-width: 100%;
  overflow-x: auto;
}

.slot-chart__plot {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-width: max-content;
  padding-block: 4px;
}

.slot-chart__column {
  display: grid;
  grid-template-rows: auto 160px auto auto;
  justify-items: center;
  gap: 4px;
  width: 36px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.slot-chart__value {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-chart__value--missing {
  color: var(--md-sys-color-on-surface-variant);
}

.slot-chart__area {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 160px;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
}

.slot-chart__bar {
  display: block;
  width: 70%;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  background: var(--md-sys-color-primary);
}

.slot-chart__slot {
  white-space: nowrap;
}

.slot-chart__posts {
  color: var(--md-sys-color-on-surface-variant);
}
</style>
