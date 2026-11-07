# Proposal

Conventional Commits: `feat(overlay)` (code PR). Phase: 6 (Overlay). Depends on: `add-content-analytics`, `add-access-control` (identity, token and session rules), `add-brand-kit` (overlay theme template), `add-brand-profiles` (personal overrides), `add-hub-mcp-server` (hub MCP tools for scene state and module actions).

Strand: overlay.

## Why

Streams need on-screen information (chat, task board, follower goal, promo cards, now-playing) that
the streamer and operators control live, without touching OBS scene files or restarting browser
sources. Today this lives in a lab prototype that is not managed centrally, cannot be configured by
the team, and cannot be themed from the brand kit or adapted per streamer.

## What Changes

- Scene templates are central and managed on the hub through the web app. A template is a layout of
  module instances with position, size and per-module configuration.
- Each person gets a personal variant of every template. The variant inherits the layout and applies
  only the overrides that the person's brand profile allows. Variants are private: others get 404.
- Each variant has a browser-source URL for OBS with a variant-scoped read-only token. The owner's OBS
  plugin creates and updates the browser sources and rotates the token; the token is never shown to
  the person.
- A module system: each module is self-contained and declares a manifest (id, version, config
  schema, data topics it subscribes to, actions it accepts). Built-in modules follow the prototype
  (chat rail, task board, follower goal, promo card, now-playing and limits). Third-party modules
  are loaded from the hub's module registry.
- Data feeds: modules subscribe to named topics over the scene's Server-Sent-Events stream (agent
  activity, stream metadata, follower and chat events, library and distribution data such as the
  next scheduled post, custom values). After a reconnect the overlay resynchronises by reading the
  current state.
- Theming: the overlay uses the brand kit's overlay theme template with the owner's profile overrides.
  A central brand change reaches every live variant that does not override that exact token.
- Operator panel (web): switch modules on and off, show promo cards, hide private areas live on the
  operator's own variant. Changes reach OBS within one second.
- Should: a "privacy curtain" module action that blanks configured regions instantly.
- Could: sandboxed (iframe) execution for third-party modules.

## Capabilities

### New Capabilities
- `overlay-scenes`: central templates, personal variants, scene browser-source URLs with variant-scoped tokens and their rotation, scene configuration.
- `overlay-modules`: module manifest, built-in module set, third-party module registry, topic subscriptions and module actions.
- `overlay-live-control`: operator panel actions on live variants (module switching, promo cards, privacy curtain), live resync and theming updates.
- `overlay-mcp-tools`: overlay scene state and module actions exposed through the hub MCP, limited to the caller's own variant and role.

### Modified Capabilities
- None. This is the first overlay change.

## Impact

- New hub endpoints for templates, variants, modules, the module registry and scene feeds (SSE), built on the access-control model.
- New web views for template editing and the operator panel.
- New overlay browser-source application (Vue 3) rendering variants from the feed.
- Depends on the brand kit for the overlay theme, on `add-brand-profiles` for brand profiles, and on access control for tokens and roles.
- The OBS plugin (`add-obs-plugin`) consumes the variant URLs and rotates tokens; it is not a dependency of this change.
- The lab prototype `overlay` is the reference for domain rules only; no code is copied.
