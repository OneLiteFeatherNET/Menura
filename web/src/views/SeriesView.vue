<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue';
import { canEditSeries, SESSION_KEY } from '../auth/session';
import { api, errorMessage } from '../api/client';
import type { Post, Series } from '../api/types';
import LoadState from '../components/LoadState.vue';
import NewSeriesDialog from '../components/NewSeriesDialog.vue';
import { useAsync } from '../composables/useAsync';
import { badgeColors } from '../domain/color';
import { audienceLabel, formatCount, formatPublished, weekdayLabel } from '../domain/format';
import { safeHttpsUrl } from '../domain/url';

// Value of the "keine Serie" entry in the assignment select.
const NO_SERIES = 'none';

const session = inject(SESSION_KEY);
const canEdit = computed(() => canEditSeries(session?.value?.roles ?? []));

const { data, error, loading, run } = useAsync(async () => {
  const [series, posts, channels, kit] = await Promise.all([
    api.series(),
    api.posts(),
    api.channels(),
    api.brandKit(),
  ]);
  return { series, posts, channels, tokens: kit.tokens };
});

onMounted(run);

const dialogOpen = ref(false);
const assignError = ref<string | null>(null);

const unassigned = computed<Post[]>(() =>
  (data.value?.posts ?? []).filter((post) => post.seriesId === null && !post.noSeries),
);

function channelName(channelId: string): string {
  return data.value?.channels.find((channel) => channel.id === channelId)?.name ?? channelId;
}

/** The first playlist link of a series, when it is an https URL. Anything else renders no link. */
function firstPlaylist(series: Series): string | undefined {
  return safeHttpsUrl(Object.values(series.playlists)[0]);
}

async function assign(post: Post, value: string): Promise<void> {
  if (value === '') {
    return;
  }
  assignError.value = null;
  try {
    await api.assignPostSeries(
      post.id,
      value === NO_SERIES ? { seriesId: null, noSeries: true } : { seriesId: value },
    );
    await run();
  } catch (cause) {
    assignError.value = errorMessage(cause);
  }
}

function onAssignChange(post: Post, event: Event): void {
  void assign(post, (event.target as HTMLSelectElement).value);
}

function onCreated(): void {
  void run();
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2>Serien</h2>
        <p class="page-lead">Wiedererkennbare Reihen der Shorts, mit Badge, Hashtag und Planungshinweis.</p>
      </div>
      <md-filled-button v-if="canEdit" @click="dialogOpen = true">Neue Serie</md-filled-button>
    </header>

    <LoadState :loading="loading" :error="error">
      <section class="section">
        <div class="series-grid">
          <article v-for="series in data?.series" :key="series.id" class="card series-card">
            <RouterLink :to="{ name: 'series-detail', params: { id: series.id } }" class="series-link">
              <span
                class="badge"
                :style="badgeColors(series.color, series.badgeTextColor)"
              >{{ series.badge.de }}</span>
              <h3>{{ series.name }}</h3>
              <span class="hashtag">{{ series.hashtag }}</span>
            </RouterLink>

            <div class="series-meta">
              <span class="chip">{{ audienceLabel(series.audience) }}</span>
              <span v-if="!series.active" class="chip">inaktiv</span>
              <span class="muted">{{ series.episodes }} Episoden</span>
            </div>
            <p class="planning">
              <span class="planning-label">Planungshinweis</span>
              {{ weekdayLabel(series.weekday) }}, {{ series.slot }} Uhr
            </p>
            <p class="muted playlist">
              <a
                v-if="firstPlaylist(series)"
                :href="firstPlaylist(series)"
                target="_blank"
                rel="noopener noreferrer"
              >Playlist öffnen</a>
              <span v-else>keine Playlist</span>
            </p>
          </article>
        </div>
      </section>

      <section class="section">
        <div>
          <h3 class="section-title">Posts ohne Serie</h3>
          <p class="muted">
            {{ unassigned.length === 0 ? 'Alle Posts sind zugeordnet.' : `${formatCount(unassigned.length)} Posts warten auf eine Zuordnung.` }}
          </p>
        </div>
        <p v-if="assignError" class="card card-error" role="alert">{{ assignError }}</p>
        <div v-if="unassigned.length > 0" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Titel</th>
                <th>Kanal</th>
                <th>Veröffentlicht</th>
                <th>Serie</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="post in unassigned" :key="post.id">
                <td>{{ post.title }}</td>
                <td>{{ channelName(post.channelId) }}</td>
                <td>{{ formatPublished(post.publishedAt) }}</td>
                <td>
                  <md-outlined-select
                    v-if="canEdit"
                    label="Serie"
                    :value="''"
                    class="assign-select"
                    @change="onAssignChange(post, $event)"
                  >
                    <md-select-option
                      v-for="series in data?.series"
                      :key="series.id"
                      :value="series.id"
                      :headline="series.name"
                    ></md-select-option>
                    <md-select-option :value="NO_SERIES" headline="keine Serie"></md-select-option>
                  </md-outlined-select>
                  <span v-else class="muted">nur Lesen</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </LoadState>

    <NewSeriesDialog
      :open="dialogOpen"
      :color-tokens="data?.tokens ?? []"
      @close="dialogOpen = false"
      @created="onCreated"
    />
  </div>
</template>

<style scoped>
.series-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.series-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.series-link {
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: inherit;
  text-decoration: none;
}

.badge {
  align-self: flex-start;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 22px;
  line-height: 28px;
  font-weight: 600;
}

.hashtag {
  color: var(--app-muted);
  font-size: 15px;
}

.series-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.planning {
  margin: 0;
  font-size: 17px;
}

.planning-label {
  display: block;
  font-size: 13px;
  color: var(--app-muted);
}

.playlist {
  margin: 0;
}

.assign-select {
  min-width: 240px;
}
</style>
