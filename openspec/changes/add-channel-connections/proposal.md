# Proposal

Conventional Commits: `feat(publish)` (code PR). Phase: 1 (Recognition & Measurability). Depends on: `add-access-control`.

Strand: platform.

## Why

Publishing to external platforms needs an account on each platform that Menura is allowed to act for. Today the only way to register a channel is to paste credentials, which puts long-lived secrets in a form, leaves no record of who connected what, and fails silently when a token is revoked or a platform app is not yet audited. Principle 13 requires each channel to talk to the platform's own API after a connection made through that platform's own consent screen. This change builds that connection layer once, so the platform changes (YouTube, Twitch, TikTok, Instagram) only add adapters.

## What Changes

- Connect a channel through the platform's own OAuth consent in the web app. Only an administrator or a publisher can start a connection; no credential is ever typed or pasted.
- Request the minimal scopes each platform adapter declares, and show the granted scopes on the channel.
- Store tokens encrypted at rest. No API, MCP tool or log output ever returns a token.
- Refresh access tokens automatically before they expire, and store rotated refresh tokens.
- Detect expired or revoked connections (including refresh failures and platform-side revocation), show them on the channel and in the calendar, and offer a reconnect action.
- Disconnect a channel: revoke the grant at the platform where it supports revocation, and delete the stored tokens either way.
- Show quota and rate-limit state per connection, as reported by the platform or measured by Menura.
- Show the platform app's audit status per channel type (for example "unaudited: uploads private only") and provide it, with the connection state, to the schedule dry run of `add-content-distribution`, which depends on this change.
- Define the platform adapter port every platform implements: publish, status, delete, capabilities, listing the channel's existing published items (platform identifier, URL, title, publish time, format) and reading their metrics. The port is specified as behaviour; adapter implementations live in the platform changes.
- Later candidates (Discord, Bluesky, Mastodon, X) are not in the first stage and get no adapter in this change.

## Capabilities

### New Capabilities
- `channel-connections`: connecting and disconnecting channels through platform consent, minimal scopes, encrypted token storage and refresh, connection health and reconnect, platform quota and audit status, and the adapter port contract.

### Modified Capabilities
- None. The channel list and posting behaviour of `content-distribution` are aligned in that change; this change only owns how a channel is connected.

## Impact

- Server: connection and token model, encryption key from environment configuration, OAuth client flow towards each platform, refresh job scheduling, and the adapter port.
- Web: "Connect channel" flow, connection status and reconnect on the channel view.
- Jobs: refresh and health check run in the `jobs` component.
- Access: starting or ending a connection requires the publisher or admin role from `add-access-control`; MCP clients see connection status but never tokens.
- Platform changes (`add-youtube-integration`, `add-twitch-integration`, and the TikTok and Instagram changes) depend on the port defined here.
