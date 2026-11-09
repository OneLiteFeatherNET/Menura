import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { expect, fn, userEvent, within } from 'storybook/test';
import PostPanel from './PostPanel.vue';
import { sampleItem, sampleRevision, COLLISION_PROBLEM, RECONNECT_PROBLEM, TEXT_PROBLEM } from './samples';

const NONE = { submit: false, approve: false, schedule: false, decideRevisions: false };

const meta = {
  title: 'Calendar/PostPanel',
  component: PostPanel,
  args: {
    item: sampleItem({ status: 'draft' }),
    actions: { ...NONE, submit: true },
    busy: false,
    refusal: null,
    revisions: [],
    revisionsStatus: 'ready',
    onClose: fn(),
    onSubmit: fn(),
    onApprove: fn(),
    onSchedule: fn(),
    onReconnect: fn(),
    onAccept: fn(),
    onReject: fn(),
    'onRetry-revisions': fn(),
    onDeclare: fn(),
  },
} satisfies Meta<typeof PostPanel>;
export default meta;

type Story = StoryObj<typeof meta>;

export const EditorSendsDraftToReview: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('heading', { name: 'Kubernetes in 30 Sekunden' })).toBeVisible();
    expect(canvas.getByText('Entwurf')).toBeVisible();
    expect(canvas.getByText('Keine Probleme gefunden.')).toBeVisible();
    expect(canvas.queryByRole('button', { name: 'Freigeben' }), 'an editor sees no approval').toBeNull();
    expect(canvas.queryByRole('button', { name: 'Einplanen' }), 'an editor sees no scheduling').toBeNull();

    await userEvent.click(canvas.getByRole('button', { name: 'Zur Prüfung senden' }));
    expect(args.onSubmit).toHaveBeenCalled();
  },
};

export const PublisherApprovesInReview: Story = {
  args: {
    item: sampleItem({ status: 'in_review' }),
    actions: { submit: false, approve: true, schedule: false, decideRevisions: true },
    revisions: [sampleRevision()],
  },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole('button', { name: 'Freigeben' }));
    expect(args.onApprove).toHaveBeenCalled();
    expect(canvas.getByText('Am Freitagabend erreicht der Kanal mehr Zuschauer.')).toBeVisible();

    await userEvent.click(canvas.getByRole('button', { name: 'Annehmen' }));
    expect(args.onAccept).toHaveBeenCalledWith('r0000000-0000-4000-8000-000000000001');
  },
};

export const ScheduleRefusedByDryRun: Story = {
  args: {
    item: sampleItem({
      status: 'approved',
      channelState: 'needs_reconnection',
      problems: [TEXT_PROBLEM, RECONNECT_PROBLEM],
    }),
    actions: { submit: false, approve: false, schedule: true, decideRevisions: true },
    refusal: {
      message: 'Die Prüfung hat Probleme gefunden. Der Beitrag wurde nicht eingeplant.',
      problems: [TEXT_PROBLEM, RECONNECT_PROBLEM],
    },
  },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Der Beitrag wurde nicht eingeplant.');
    expect(canvas.getByText('Der Text ist für YouTube zu lang (höchstens 5000 Zeichen).')).toBeVisible();

    await userEvent.click(canvas.getByRole('button', { name: 'Kanal neu verbinden' }));
    expect(args.onReconnect).toHaveBeenCalled();
    await userEvent.click(canvas.getByRole('button', { name: 'Einplanen' }));
    expect(args.onSchedule).toHaveBeenCalled();
  },
};

export const CollisionWarningDoesNotBlock: Story = {
  args: {
    item: sampleItem({ status: 'approved', problems: [COLLISION_PROBLEM] }),
    actions: { submit: false, approve: false, schedule: true, decideRevisions: false },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Hinweis')).toBeVisible();
    expect(canvas.getByRole('button', { name: 'Einplanen' })).toBeEnabled();
    expect(canvas.queryByRole('button', { name: 'Kanal neu verbinden' })).toBeNull();
  },
};

export const FailedWithAttempts: Story = {
  args: {
    item: sampleItem({
      status: 'failed',
      failureReason: 'Die Plattform hat den Upload abgelehnt.',
      attempts: 2,
      steps: [
        { step: 'upload', state: 'done', changedAt: '2026-11-09T17:00:00Z', reason: null },
        { step: 'publish', state: 'failed', changedAt: '2026-11-09T17:05:00Z', reason: 'Zeitüberschreitung' },
      ],
      attemptHistory: [
        { attempt: 1, startedAt: '2026-11-09T17:00:00Z', finishedAt: '2026-11-09T17:01:00Z', outcome: 'retry_scheduled', reason: 'Temporär' },
        { attempt: 2, startedAt: '2026-11-09T17:05:00Z', finishedAt: null, outcome: 'unknown', reason: null },
      ],
    }),
    actions: { submit: false, approve: false, schedule: true, decideRevisions: false },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Die Plattform hat den Upload abgelehnt.');
    expect(canvas.getByText('Veröffentlichung: Fehlgeschlagen', { exact: false })).toBeVisible();
    expect(canvas.getByText('Versuch 2:', { exact: false })).toHaveTextContent('Ergebnis unbekannt');
  },
};

const YOUTUBE_QUOTA = {
  unitsPerDay: 10000,
  unitsRemaining: 9800,
  uploadsPerDay: 6,
  uploadsRemaining: 5,
  resetsAt: '2026-11-09T08:00:00Z',
};

export const YouTubeDeclarationOpen: Story = {
  args: {
    item: sampleItem({ status: 'draft', platform: 'youtube', visibilityDeclared: false }),
    actions: { submit: true, approve: false, schedule: false, decideRevisions: false },
    canDeclare: true,
    quota: YOUTUBE_QUOTA,
  },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('noch nicht gewählt')).toBeVisible();
    expect(canvas.getByText('9800 von 10000 Einheiten · 5 von 6 Uploads frei', { exact: false })).toBeVisible();

    await userEvent.click(canvas.getByRole('combobox', { name: 'Sichtbarkeit' }));
    await userEvent.click(canvas.getByRole('option', { name: 'Privat' }));
    await userEvent.click(canvas.getByRole('radio', { name: /Nein, das Video ist nicht für Kinder bestimmt/ }));
    await userEvent.click(canvas.getByRole('button', { name: 'Angaben speichern' }));

    expect(args.onDeclare).toHaveBeenCalledWith({ visibility: 'private', madeForKids: false, planShort: false });
  },
};

export const YouTubeDeclarationReadOnlyWhenScheduled: Story = {
  args: {
    item: sampleItem({ status: 'scheduled', platform: 'youtube', visibilityDeclared: true, visibility: 'private', madeForKids: false }),
    actions: NONE,
    canDeclare: false,
    quota: null,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.queryByRole('button', { name: 'Angaben speichern' }), 'a scheduled post takes no declaration').toBeNull();
    // Made for kids is no, and the post is not planned as a Short.
    expect(canvas.getAllByText('Nein')).toHaveLength(2);
  },
};

export const AppliedVisibilityMismatch: Story = {
  args: {
    item: sampleItem({
      status: 'scheduled',
      platform: 'youtube',
      visibilityDeclared: true,
      visibility: 'public',
      appliedVisibility: 'private',
      visibilityProblem: 'The platform applies private instead of public.',
    }),
    actions: NONE,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole('alert')).toHaveTextContent('Angewendet: Privat, gewählt: Öffentlich.');
    expect(canvas.queryByText(/platform applies/), 'the server text is not shown').toBeNull();
  },
};

export const DeclarationProblemsInGerman: Story = {
  args: {
    item: sampleItem({ status: 'approved', platform: 'youtube', problems: [] }),
    actions: { submit: false, approve: false, schedule: true, decideRevisions: false },
    refusal: {
      message: 'Die Prüfung hat Probleme gefunden. Der Beitrag wurde nicht eingeplant.',
      problems: [
        { code: 'visibility_undeclared', severity: 'error', message: 'Choose the visibility', action: null },
        { code: 'made_for_kids_missing', severity: 'error', message: 'Declare made for kids', action: null },
        { code: 'short_too_long', severity: 'error', message: 'Too long', action: null },
      ],
    },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText('Wählen Sie die Sichtbarkeit des Beitrags, bevor er eingeplant wird.')).toBeVisible();
    expect(canvas.getByText('Beantworten Sie, ob das Video für Kinder bestimmt ist, bevor es eingeplant wird.')).toBeVisible();
    expect(canvas.getByText('Ein Short darf höchstens drei Minuten lang sein.')).toBeVisible();
    expect(canvas.queryByText('Choose the visibility')).toBeNull();
  },
};

export const ViewerReadsOnly: Story = {
  args: { actions: NONE },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    for (const name of ['Zur Prüfung senden', 'Freigeben', 'Einplanen']) {
      expect(canvas.queryByRole('button', { name }), `no ${name} for a viewer`).toBeNull();
    }
  },
};
