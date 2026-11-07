<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../api/client';
import FirstJoins from '../components/FirstJoins.vue';
import LoadState from '../components/LoadState.vue';
import ViewPercent from '../components/ViewPercent.vue';
import { useAsync } from '../composables/useAsync';
import { latestWeekOf, weeksOf } from '../domain/attribution';
import { audienceLabel, formatCount } from '../domain/format';

const selectedWeek = ref('');

// The weeks offered in the select come from the attribution data; the comparison follows the selection.
const weeksList = useAsync(async () => {
  const weekly = await api.weeklyAttribution();
  return { weeks: weeksOf(weekly), latest: latestWeekOf(weekly) };
});

const comparison = useAsync(() => api.seriesComparison(selectedWeek.value || undefined));

onMounted(async () => {
  await weeksList.run();
  selectedWeek.value = weeksList.data.value?.latest ?? '';
});

// Changing the week reloads the comparison. The first selection is made by onMounted above.
watch(selectedWeek, (week) => {
  if (week !== '') {
    void comparison.run();
  }
});

const weekOptions = computed(() => (weeksList.data.value?.weeks ?? []).slice().reverse());

const rows = computed(() => comparison.data.value ?? []);

function onWeekChange(event: Event): void {
  selectedWeek.value = (event.target as HTMLSelectElement).value;
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Analytics</h2>
        <p class="page-lead">
          Serien im Vergleich. Sortiert nach Engaged Views, so wie sie geliefert wurden.
        </p>
      </div>
      <LoadState :loading="weeksList.loading.value" :error="weeksList.error.value">
        <md-outlined-select label="Woche" :value="selectedWeek" @change="onWeekChange">
          <md-select-option
            v-for="week in weekOptions"
            :key="week"
            :value="week"
            :headline="week"
          ></md-select-option>
        </md-outlined-select>
      </LoadState>
    </header>

    <LoadState :loading="comparison.loading.value" :error="comparison.error.value">
      <p v-if="rows.length === 0" class="muted">Für diese Woche liegen keine Serien vor.</p>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Serie</th>
              <th>Zielgruppe</th>
              <th class="num">Posts</th>
              <th class="num">Engaged Views</th>
              <th class="num">Ø-View-%</th>
              <th class="num">Erstjoins</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.seriesId">
              <td>{{ row.name }}</td>
              <td><span class="chip">{{ audienceLabel(row.audience) }}</span></td>
              <td class="num">{{ formatCount(row.posts) }}</td>
              <td class="num">{{ formatCount(row.engagedViews) }}</td>
              <td class="num"><ViewPercent :value="row.avgViewPercent" /></td>
              <td class="num">
                <FirstJoins :value="row.estimatedFirstJoins" :estimated="row.estimate" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="muted footnote">
        Erstjoins einer Serie sind Schätzungen: Ein Join über den Kanal-Host wird gleichmäßig auf die
        Posts des Kanals verteilt, die in den 72 Stunden davor erschienen sind.
      </p>
    </LoadState>
  </div>
</template>

<style scoped>
.footnote {
  margin: 0;
  font-size: 14px;
  max-width: 880px;
}
</style>
