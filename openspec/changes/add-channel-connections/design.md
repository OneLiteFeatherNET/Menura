# Design

## Context

Channels are the external accounts that posts are published to (`add-content-distribution`). Principle 13 requires each channel to talk to the platform's official API through its own adapter, to be connected through the platform's own OAuth consent in the web app, and never to receive pasted credentials. Principle 6 covers Menura's own sign-in; this change covers the grants Menura receives from platforms. See proposal.md - Why for motivation.

Principle 11 applies: secrets never appear in logs or API output, and hidden objects answer 404. Principle 1 applies: tokens live in PostgreSQL, encrypted; no other store.

## Goals / Non-Goals

**Goals:**
- One connection flow and one health model for all platforms, so each platform change only adds an adapter.
- Tokens that cannot leak through any response, log or export.
- A connection problem is visible before a scheduled post fails.

**Non-Goals:**
- No adapter implementation for any platform; those live in the platform changes.
- No pasting of credentials, no personal access tokens, no third-party aggregator (principle 13).
- No consent for scopes a feature does not need yet.

## Platform constraints (verified 2026-11-02)

The facts below were checked against the official documentation on 2026-11-02 and affect the connection layer. Platform-specific constraints are recorded in the platform changes.

- Twitch access tokens expire; the response's `expires_in` gives the lifetime, and an invalid token is answered with HTTP 401. Refresh responses include a new refresh token that must be stored for the next refresh. Refresh tokens of Public-type clients expire 30 days after generation; a refresh can fail with 401 when the user changed the password or disconnected the app, and then a new authorization code grant is required. Source: https://dev.twitch.tv/docs/authentication/refresh-tokens/ (checked 2026-11-02).
- Twitch rate limits are counted in separate buckets for app and user access requests, per client ID and per user for user tokens; the default cost per request is 1 point. The numeric per-minute limit was not stated on the page that was read, so it is not used as a constant in this design. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).
- Twitch states that breaking changes are announced in the Developer Forum announcement section before they take effect. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).
- YouTube: API projects that have not passed an audit are limited in quota; the audit and quota extension form is the route to more quota, and a completed compliance audit allows an extension request within 12 months. Source: https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits (checked 2026-11-02).
- YouTube: the resource documentation for videos states "Videos uploaded from unverified API projects are not restricted to private viewing mode." This contradicts the product team's starting point that unaudited uploads are locked to private. Older third-party and forum sources describe the lock. The design therefore reads back the visibility the platform actually applied and does not assume either rule. Source: https://developers.google.com/youtube/v3/docs/videos (checked 2026-11-02). Details for the YouTube change are in `add-youtube-integration`.

## Decisions

**Connection through the platform's consent in the web app.** The web app (backend-for-frontend) starts an authorization code flow with the platform; the callback is handled by the server, which stores the grant bound to the channel. The browser never sees a token.
- Rejected: pasting tokens or keys into a form. It keeps long-lived secrets in the browser and in the operator's clipboard, and cannot be refreshed.
- Rejected: a connection created by an MCP client. Starting a connection is an account decision and needs a person in the consent screen (principle 4).

**Tokens encrypted with a key from environment configuration.** Access and refresh tokens are stored as ciphertext in PostgreSQL; the encryption key is supplied through environment configuration (principle 8), and a key rotation re-encrypts stored grants.
- Rejected: plain columns. A database dump would expose every account.
- Rejected: one Kubernetes secret per channel created at runtime. It needs cluster API permissions for the application (see the earlier distribution design).

**Channel representation without a token field.** The API and MCP representations of a channel have no field for tokens, so they cannot be serialised by accident. Health, scopes and expiry are separate fields.
- Rejected: a shared "credentials" object with a masked value. Masking still exposes a fragment and invites copies.

**Refresh and health check as a scheduled job.** The `jobs` component runs a health check that refreshes tokens within a margin before expiry, and marks connections that fail. A publication attempt also refreshes if the token is near expiry, so a publication does not depend on the timing of the health check.
- Rejected: refreshing only on demand. A post due at 09:00 would then discover an expired grant at 09:00.

**Adapter port as a fixed set of operations.** Each adapter implements publish, status, delete, capabilities, listing of the channel's existing published items, and reading metrics for them. The listing is needed in phase 1 by `add-external-post-import` (existing posts of every connected channel), and metrics by `add-content-analytics`, so both are part of the port from the start. Capabilities describe features such as scheduled publishing, chapters, playlists or thumbnails, so the common code never branches on platform name.
- Rejected: a generic posting form with per-platform switches. Platform rules differ too much to keep one code path correct.

**Audit status recorded per platform app.** Each channel type has an audit status (unknown, unaudited, audited) that an administrator records with a date and the source of the decision. The status drives restrictions in the dry run; "unknown" is treated as restricted for public visibility.
- Rejected: hard-coding audit states in the adapters. Audit results change without a release.

**Revocation attempted on disconnect, deletion always.** Where a platform offers token revocation, Menura requests it; the tokens are deleted in every case, so a failed revocation cannot keep a working token in Menura.

## Risks / Trade-offs

- [Risk] Refresh token expires (Twitch public clients after 30 days) while nobody is looking → Mitigation: the health check reports the expiry date ahead of time and the connection is shown as needing reconnection before it fails.
- [Risk] An encryption key is lost → Mitigation: key material is held in the cluster's secret store under the existing backup process; a lost key means reconnecting all channels, which the reconnect flow supports without losing history.
- [Risk] A platform changes its consent or scope names → Mitigation: scopes are declared per adapter and a changed scope is shown as missing rather than failing silently.
- [Risk] Audit status is recorded wrongly by an administrator → Mitigation: the status carries a date and a source, and the dry run shows it so a wrong value is visible at scheduling time.
- [Trade-off] Refresh and health checks add a periodic job and load on platform token endpoints; the interval is configurable.

## Migration Plan

New tables and the encryption key are added with forward-only migrations. No existing channels are migrated automatically; channels that were created before this change are shown as "not connected" and must be connected through consent. Rollback deploys the previous server release; stored grants remain encrypted and unused, and scheduled publications stop when the CronJob is suspended.

## Open Questions

- Refresh margin and health check interval: configured through environment variables with defaults; tuned after the first live week, so the specs do not change.
