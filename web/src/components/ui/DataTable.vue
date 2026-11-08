<script setup lang="ts">
// Rows are plain data. With selectable set (editors), the first column's text is a button that reports the row; a
// viewer sees the same text without a control. Cells can be replaced through a slot named cell-<column key>.
export interface DataColumn {
  key: string;
  label: string;
}

const props = withDefaults(
  defineProps<{
    caption: string;
    columns: DataColumn[];
    rows: Record<string, unknown>[];
    rowKey: string;
    emptyText?: string;
    selectable?: boolean;
  }>(),
  { emptyText: 'Keine Einträge.', selectable: false },
);

const emit = defineEmits<{ select: [row: Record<string, unknown>] }>();

function text(row: Record<string, unknown>, key: string): string {
  const value = row[key];
  return value === undefined || value === null ? '' : String(value);
}

const first = () => props.columns[0]?.key;
</script>

<template>
  <div class="data-table">
    <table class="data-table__table">
      <caption class="data-table__caption">{{ caption }}</caption>
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" scope="col" class="data-table__head">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="rows.length === 0">
          <td class="data-table__empty" :colspan="columns.length">{{ emptyText }}</td>
        </tr>
        <tr v-for="row in rows" :key="text(row, rowKey)">
          <td v-for="column in columns" :key="column.key" class="data-table__cell">
            <slot :name="`cell-${column.key}`" :row="row">
              <button
                v-if="selectable && column.key === first()"
                type="button"
                class="data-table__select"
                @click="emit('select', row)"
              >
                {{ text(row, column.key) }}
              </button>
              <template v-else>{{ text(row, column.key) }}</template>
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.data-table {
  max-width: 100%;
  overflow-x: auto;
  font-family: Roboto, system-ui, sans-serif;
  color: var(--md-sys-color-on-surface);
}

.data-table__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table__caption {
  padding: 0 0 8px;
  text-align: start;
  font-size: 14px;
  color: var(--md-sys-color-on-surface-variant);
}

.data-table__head,
.data-table__cell,
.data-table__empty {
  padding: 8px 12px;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  text-align: start;
  vertical-align: top;
  overflow-wrap: anywhere;
}

.data-table__head {
  font-weight: 500;
  color: var(--md-sys-color-on-surface-variant);
}

.data-table__empty {
  color: var(--md-sys-color-on-surface-variant);
}

.data-table__select {
  padding: 0;
  border: 0;
  background: none;
  color: var(--md-sys-color-primary);
  font: inherit;
  text-align: start;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.data-table__select:focus-visible {
  outline: 2px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
</style>
