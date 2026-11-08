<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  disconnectChannel,
  listChannels,
  listPlatforms,
  recordAuditStatus,
  startConnection,
  startReconnection,
  type AuditStatusValue,
  type Channel,
  type Platform,
} from '../../api/channels';
import ChannelsView from './ChannelsView.vue';
import {
  connectionNoticeText,
  connectionPermissions,
  describeError,
  parseConnectionNotice,
  revocationText,
} from './model';

// The container of the channels page: it loads the data, performs the actions through the API and holds the state of
// the dialogs. The browser is only sent to a platform's consent URL; no token is read or stored here.
const props = defineProps<{ roles: string[] }>();

const status = ref<'loading' | 'error' | 'ready'>('loading');
const channels = ref<Channel[]>([]);
const platforms = ref<Platform[]>([]);
const busy = ref(false);
const actionError = ref<string | null>(null);
const actionNotice = ref<string | null>(null);
const connectOpen = ref(false);
const auditTarget = ref<Platform | null>(null);

// The consent callback returns the outcome in the query string of this page; it is read once when the page opens.
const consent = parseConnectionNotice(window.location.search);
const consentText = consent ? connectionNoticeText(consent) : null;
const notice = computed(() => actionNotice.value ?? consentText);
const permissions = computed(() => connectionPermissions(props.roles));

async function load() {
  status.value = 'loading';
  try {
    const [loadedPlatforms, loadedChannels] = await Promise.all([listPlatforms(), listChannels()]);
    platforms.value = loadedPlatforms;
    channels.value = loadedChannels;
    status.value = 'ready';
  } catch {
    status.value = 'error';
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true;
  actionError.value = null;
  actionNotice.value = null;
  try {
    await action();
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    busy.value = false;
  }
}

/** Sends the browser to the platform's consent; the callback brings it back to this page. */
function connect(platform: string) {
  connectOpen.value = false;
  return run(async () => {
    window.location.assign(await startConnection(platform));
  });
}

function reconnect(channel: Channel) {
  return run(async () => {
    window.location.assign(await startReconnection(channel.id));
  });
}

function disconnect(channel: Channel) {
  return run(async () => {
    const revocation = await disconnectChannel(channel.id);
    channels.value = await listChannels();
    actionNotice.value = `Der Kanal „${channel.displayName}“ wurde getrennt. ${revocationText(revocation)}`;
  });
}

function saveAudit(request: { platform: string; status: AuditStatusValue; source: string }) {
  return run(async () => {
    await recordAuditStatus(request.platform, { status: request.status, source: request.source || undefined });
    platforms.value = await listPlatforms();
    auditTarget.value = null;
    actionNotice.value = 'Der Prüfstatus wurde gespeichert.';
  });
}

onMounted(load);
</script>

<template>
  <ChannelsView
    :status="status"
    :platforms="platforms"
    :channels="channels"
    :permissions="permissions"
    :busy="busy"
    :notice="notice"
    :action-error="actionError"
    :connect-open="connectOpen"
    :audit-target="auditTarget"
    @retry="load"
    @open-connect="connectOpen = true"
    @close-connect="connectOpen = false"
    @connect="connect"
    @reconnect="reconnect"
    @disconnect="disconnect"
    @open-audit="(platform) => (auditTarget = platform)"
    @close-audit="auditTarget = null"
    @save-audit="saveAudit"
  />
</template>
