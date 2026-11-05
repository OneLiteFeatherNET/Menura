# Design

## Context

The hub already has access control from `add-access-control`: roles for editing and reading, and a token per consumer. The overlay renders in a browser source, the OBS plugin syncs scenes into OBS, the desktop app renders locally with ffmpeg, and the web app edits. None of them owns the look today. See proposal.md - Why for the motivation.

Constraints from the project context: the cluster runs no ffmpeg and no rendering, every client holds its own SSE stream, and the server is the source of truth for brand data.

## Goals / Non-Goals

**Goals:**
- One versioned kit that every consumer reads the same way, with pinning and change notification.
- Templates that cannot drift from the palette, because they can only reference tokens.
- Accessibility of text roles checked at publication time, not by reviewers after the fact.

**Non-Goals:**
- No server-side rendering of templates to images or video (principle 2). The server stores and validates templates; the desktop app and the overlay render them.
- No editing of video or overlay scenes here; scene layouts belong to `overlay-scenes`.
- Sub-brands are only a Could item; no design work beyond inheritance is done here.

## Decisions

**Whole-kit immutable versions.** Each saved change stores the complete kit (palette, typography, marks, texts and templates) as one version record. Pinning and reading an old version then need no reconstruction.
- Rejected: per-field history with replay. Reconstructing a pinned state from deltas is error-prone and makes "version 2 still readable" depend on every later change.
- Rejected: a mutable record plus an audit log. Pinned consumers need a stable document, not a reconstruction of one.

**Tonal palette computed on the server.** Tones are derived from each seed colour by a fixed tonal-palette algorithm (Material Design 3 HCT tones), run in the server when a version is saved. Stored tones are then part of the version, so every consumer gets identical values.
- Rejected: tones typed in by hand. They drift from their seeds over time.
- Rejected: computing tones in each client. The overlay, desktop and web would each carry the algorithm and could disagree.

**Contrast gate on publication.** The server computes the WCAG contrast ratio for each text role against its background role and refuses publication below AA. Publishing is the moment the kit becomes authoritative, so the gate sits there.
- Rejected: warnings only. Text that fails contrast reaches stream overlays and thumbnails unnoticed.

**Templates as declarative documents.** A template describes elements, token references and declared parameters. It contains no pixel code. Desktop and overlay interpret it. This keeps the server free of rendering (principle 2) and keeps one format for all consumers.
- Rejected: templates as images or HTML snapshots. They cannot be filled with parameters or re-themed.

**Notification carries the version number only.** The event on the SSE stream names the new version; clients read the kit they need. This matches principle 3 (resynchronise by reading state after a reconnect).
- Rejected: pushing the full kit in the event. It bloats every stream and duplicates the read path.

**Pins stored per consumer.** A consumer (an overlay scene, an OBS plugin installation, a desktop installation) stores its pinned version on the hub. Unpinned consumers follow the latest version.
- Rejected: pinning in the client only. A client reinstall would silently jump to the latest kit.

**Per-person profiles as override documents.** A profile stores only the overrides, keyed by token or asset identifier, and is resolved on read against the kit version a consumer pinned. Inheritance is therefore computed, never copied, so a central change needs no per-person write and reaches everyone on their next read or notification.
- Rejected: copying the whole kit into each profile. Every central change would then need a write per person, and an override would be indistinguishable from a copied value.
- Rejected: free-form overrides of any token. The kit must stay the standard; the overridable markers are the only place where individuality is allowed.

**Profile overrides reuse the publication gate.** A text-role override runs the same WCAG AA check as the central kit, so a personal colour cannot break readability on stream.

**Templates versioned with the kit.** A template change creates a new kit version, so one version number describes the whole look. This is an assumption made here: the brief asks for versioning of the kit and of templates together but does not say whether they version separately.

## Risks / Trade-offs

- [Risk] Overlay browser sources cache old theme values → Mitigation: the theme request includes the version number, so a new version is a new URL.
- [Risk] Tonal palette algorithm output changes between releases, shifting colours → Mitigation: the derived tones are stored in each version, so an existing version never changes when the algorithm is updated.
- [Risk] Template parameters grow into a second language → Mitigation: only the parameters title, subtitle and image slot are required; further parameters need a change proposal.
- [Trade-off] Storing whole versions uses more space than deltas; the kit is small, so the cost is negligible compared with media.

## Migration Plan

First release of the brand kit: the initial version is created from the current OLF colours, fonts and marks, supplied by the marketing owner. No consumer exists before the overlay, OBS plugin and desktop changes, so nothing is migrated in production. Rollback leaves the stored versions in place and stops publication of new versions.

## Open Questions

- Should the tonal-palette algorithm be reviewed by design before the first kit is published? The stored tones make later changes safe, so this can be settled before the first publication without changing the specs.
