<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(
  defineProps<{
    label: string;
    options: SelectOption[];
    modelValue?: string | null;
    placeholder?: string;
    disabled?: boolean;
    error?: string;
  }>(),
  { modelValue: null, placeholder: 'Auswählen', disabled: false, error: undefined },
);

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

// Why the menu is an in-flow listbox and not md-outlined-select: the material menu is positioned over the page
// (popover or absolute), so an open menu covers the controls below it, and the prototype's options rendered
// without text. This listbox expands inside the layout, so the controls below move down instead of being covered,
// and every option is ordinary text that tests can measure. Keyboard use follows the listbox pattern.
const uid = useId();
const labelId = `${uid}-label`;
const listId = `${uid}-list`;
const errorId = `${uid}-error`;
const triggerRef = ref<HTMLButtonElement | null>(null);
const optionRefs = ref<HTMLLIElement[]>([]);
const open = ref(false);

const selected = computed(() => props.options.find((o) => o.value === props.modelValue) ?? null);

function enabledIndexes(): number[] {
  return props.options.map((o, i) => (o.disabled ? -1 : i)).filter((i) => i >= 0);
}

async function openMenu(focusIndex?: number) {
  if (props.disabled) return;
  open.value = true;
  await nextTick();
  const enabled = enabledIndexes();
  const target = focusIndex ?? enabled.find((i) => props.options[i]?.value === props.modelValue) ?? enabled[0];
  if (target !== undefined) focusOption(target);
}

function closeMenu(returnFocus: boolean) {
  open.value = false;
  if (returnFocus) triggerRef.value?.focus();
}

function focusOption(index: number) {
  optionRefs.value[index]?.focus();
}

function choose(option: SelectOption) {
  if (option.disabled) return;
  emit('update:modelValue', option.value);
  closeMenu(true);
}

function moveFocus(from: number, step: 1 | -1) {
  const enabled = enabledIndexes();
  if (enabled.length === 0) return;
  const position = enabled.indexOf(from);
  const next = enabled[(position + step + enabled.length) % enabled.length];
  if (next !== undefined) focusOption(next);
}

function onTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    void openMenu();
  }
}

function onOptionKeydown(event: KeyboardEvent, index: number) {
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault();
      moveFocus(index, 1);
      break;
    case 'ArrowUp':
      event.preventDefault();
      moveFocus(index, -1);
      break;
    case 'Home':
      event.preventDefault();
      focusOption(enabledIndexes()[0] ?? index);
      break;
    case 'End':
      event.preventDefault();
      focusOption(enabledIndexes().at(-1) ?? index);
      break;
    case 'Enter':
    case ' ':
      event.preventDefault();
      choose(props.options[index]!);
      break;
    case 'Escape':
      event.preventDefault();
      closeMenu(true);
      break;
    case 'Tab':
      closeMenu(false);
      break;
  }
}

function onFocusOut(event: FocusEvent) {
  const wrapper = event.currentTarget as HTMLElement;
  const next = event.relatedTarget as Node | null;
  if (open.value && (!next || !wrapper.contains(next))) closeMenu(false);
}

function toggle() {
  if (open.value) closeMenu(true);
  else void openMenu();
}
</script>

<template>
  <div class="app-select" :class="{ 'app-select--error': !!error, 'app-select--disabled': disabled }" @focusout="onFocusOut">
    <span :id="labelId" class="app-select__label">{{ label }}</span>
    <button
      ref="triggerRef"
      type="button"
      role="combobox"
      class="app-select__trigger"
      aria-haspopup="listbox"
      :aria-expanded="open && options.length ? 'true' : 'false'"
      :aria-controls="open && options.length ? listId : undefined"
      :aria-labelledby="labelId"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="error ? errorId : undefined"
      :disabled="disabled"
      @click="toggle"
      @keydown="onTriggerKeydown"
    >
      <span :class="{ 'app-select__placeholder': !selected }">{{ selected ? selected.label : placeholder }}</span>
      <span class="app-select__chevron" aria-hidden="true">▾</span>
    </button>
    <p v-if="open && options.length === 0" class="app-select__empty">Keine Auswahl verfügbar.</p>
    <ul
      v-else-if="open"
      :id="listId"
      role="listbox"
      class="app-select__list"
      :aria-labelledby="labelId"
    >
      <li
        v-for="(option, index) in options"
        :key="option.value"
        :ref="(el) => { if (el) optionRefs[index] = el as HTMLLIElement }"
        role="option"
        class="app-select__option"
        tabindex="-1"
        :aria-selected="option.value === modelValue ? 'true' : 'false'"
        :aria-disabled="option.disabled ? 'true' : undefined"
        @click="choose(option)"
        @keydown="onOptionKeydown($event, index)"
      >
        {{ option.label }}
      </li>
    </ul>
    <p v-if="error" :id="errorId" class="app-select__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.app-select {
  position: relative;
  display: grid;
  gap: 4px;
  min-width: 0;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.app-select__label {
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-on-surface-variant);
  overflow-wrap: anywhere;
}

.app-select__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 48px;
  padding: 0 12px;
  border: 1px solid var(--md-sys-color-outline);
  border-radius: 4px;
  background: var(--md-sys-color-surface);
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
}

.app-select__trigger:focus-visible,
.app-select__option:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}

.app-select__trigger > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}

.app-select__placeholder {
  color: var(--md-sys-color-on-surface-variant);
}

.app-select__chevron {
  flex: none;
}

.app-select__list {
  margin: 0;
  padding: 4px 0;
  list-style: none;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 4px;
  background: var(--md-sys-color-surface-container, var(--md-sys-color-surface));
}

.app-select__option {
  min-height: 40px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.app-select__option[aria-selected='true'] {
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}

.app-select__option[aria-disabled='true'] {
  color: var(--md-sys-color-outline);
  cursor: default;
}

.app-select__empty {
  margin: 0;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 20px;
  color: var(--md-sys-color-on-surface-variant);
}

.app-select--error .app-select__trigger {
  border-color: var(--md-sys-color-error);
}

.app-select__error {
  margin: 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--md-sys-color-error);
}

.app-select--disabled .app-select__trigger {
  opacity: 0.38;
  cursor: default;
}
</style>
