<script setup lang="ts">
import { computed, inject, onMounted } from 'vue';
import { api } from '../api/client';
import type { Session } from '../api/types';
import { canEditSeries, SESSION_KEY } from '../auth/session';
import FirstJoins from '../components/FirstJoins.vue';
import LoadState from '../components/LoadState.vue';
import ViewPercent from '../components/ViewPercent.vue';
import { useAsync } from '../composables/useAsync';
import { badgeColors } from '../domain/color';
import {
  audienceLabel,
  formatCount,
  formatPublished,
  weekdayLabel,
} from '../domain/format';
import SeriesPlanForm from '../features/calendar/SeriesPlanForm.vue';

const props = defineProps<{
  id: string;
}>();

const session = inject(SESSION_KEY, computed<Session | null>(() => null));
const canEdit = computed(() => session.value !== null && canEditSeries(session.value.roles));

const { data, error, loading, run } = useAsync(async () => {
  const [series, posts, channels] = await Promise.all([
    api.series(),
    api.posts({ seriesId: props.id }),
    api.channels(),
  ]);
  const found = series.find((candidate) => candidate.id === props.id) ?? null;
  return { series: found, posts, channels };
});

onMounted(run);

const sortedPosts = computed(() =>
  [...(data.value?.posts ?? [])].sort((a, b) => (a.episode ?? 1e9) - (b.episode ?? 1e9)),
);

function channelName(channelId: string): string {
  return data.value?.channels.find((channel) => channel.id === channelId)?.name ?? channelId;
}
</script>

<template>
  <div class="page">
    <RouterLink to="/serien" class="back">← Alle Serien</RouterLink>

    <LoadState :loading="loading" :error="error">
      <p v-if="data && !data.series" class="card card-error" role="alert">
        Diese Serie gibt es nicht.
      </p>

      <template v-else-if="data?.series">
        <header class="page-head">
          <div class="head-main">
            <span
              class="badge"
              :style="badgeColors(data.series.color, data.series.badgeTextColor)"
            >{{ data.series.badge.de }}</span>
            <h2>{{ data.series.name }}</h2>
            <p class="page-lead">
              {{ data.series.hashtag }} · {{ audienceLabel(data.series.audience) }} ·
              Planungshinweis {{ weekdayLabel(data.series.weekday) }}, {{ data.series.slot }} Uhr
            </p>
          </div>
          <span class="chip">{{ formatCount(data.series.episodes) }} Episoden vergeben</span>
        </header>

        <section v-if="canEdit" class="section">
          <SeriesPlanForm :series-id="data.series.id" />
        </section>

        <section class="section">
          <h3 class="section-title">Posts dieser Serie</h3>
          <p v-if="sortedPosts.length === 0" class="muted">Noch keine Posts in dieser Serie.</p>
          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="num">Episode</th>
                  <th>Titel</th>
                  <th>Kanal</th>
                  <th>Veröffentlicht</th>
                  <th class="num">Engaged Views</th>
                  <th class="num">Ø-View-%</th>
                  <th class="num">Erstjoins</th>
                  <th>Playlist</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="post in sortedPosts" :key="post.id">
                  <td class="num">{{ post.episode ?? '–' }}</td>
                  <td>{{ post.title }}</td>
                  <td>{{ channelName(post.channelId) }}</td>
                  <td>{{ formatPublished(post.publishedAt) }}</td>
                  <td class="num">{{ formatCount(post.engagedViews) }}</td>
                  <td class="num"><ViewPercent :value="post.avgViewPercent" /></td>
                  <td class="num">
                    <FirstJoins :value="post.estimatedFirstJoins" :estimated="true" />
                  </td>
                  <td>
                    <span :class="post.inPlaylist ? 'ok' : 'warn'">
                      {{ post.inPlaylist ? 'in Playlist' : 'fehlt in Playlist' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="muted footnote">
            Erstjoins werden pro Clip geschätzt (72-h-Fenster nach Veröffentlichung).
          </p>
        </section>
      </template>
    </LoadState>
  </div>
</template>

<style scoped>
.back {
  font-size: 15px;
}

.head-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.badge {
  align-self: flex-start;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 22px;
  line-height: 28px;
  font-weight: 600;
}

.ok {
  color: var(--md-sys-color-primary);
}

.warn {
  color: var(--md-sys-color-error);
}

.footnote {
  margin: 0;
  font-size: 14px;
}
</style>
