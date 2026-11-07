# Design

## Context

The hub already has access control from `add-access-control`: roles for editing and reading, and a token per consumer. The stream overlay renders in a browser source and already defines the look of the stream through its colour roles. The OBS plugin syncs scenes into OBS, and the shorts are cut and rendered by an external renderer (the current cut tool) until `add-local-editing-mcp` moves rendering into the desktop app. None of them owns the look today; the cut tool holds its own copy of the colours, and that copy has drifted. See proposal.md - Why for the motivation.

Constraints from the project context: the cluster runs no ffmpeg and no rendering, every client holds its own SSE stream, and the server is the source of truth for brand data.

## Goals / Non-Goals

**Goals:**
- One versioned kit that every consumer reads the same way, with pinning and change notification.
- Templates that cannot drift from the palette, because they can only reference tokens.
- Accessibility of text roles checked at publication time, not by reviewers after the fact.
- Shorts recognisable in the first second: the badge is visible throughout, the title only in the first three seconds, and captions and marks stay in the safe zone.

**Non-Goals:**
- No server-side rendering of templates to images or video (principle 2). The server stores and validates templates; the external renderer and, later, the desktop app render them.
- No editing of video or overlay scenes here; scene layouts belong to `overlay-scenes`.
- No palette editor and no per-person variants here; they are in `add-brand-profiles`.

## Decisions

**Brand kit core in Phase 1.** The core ships in Phase 1 so that shorts carry the brand from the first stream after this change. Profiles, the palette editor, sub-brands and the brand MCP resources stay in Phase 5 (`add-brand-profiles`).
- Rejected: wait for Phase 5. Shorts would keep being cut against the cut tool's own colours for another phase.

**External renderer reads a JSON render bundle until Phase 5.** The cut tool reads a JSON render bundle of a named kit version and draws the shorts from it. Menura does not render anything until `add-local-editing-mcp` ships.
- Rejected: Menura renders in Phase 1. That would pull ingest, the content library and the media job queue forward into Phase 1.
- Rejected: keep the look in the renderer's code. It has already drifted from the overlay, and a second copy of the palette would drift again.

**Seed from the overlay theme.** The first version takes its colour roles from the stream overlay theme (its Material Design 3 CSS custom properties) and its mark from the feather logo, because the overlay is what viewers already see on stream.
- Rejected: the hex values hard-coded in the cut tool. They drifted from the overlay, and seeding from them would make the first version a copy of the wrong look.

**No end card, call to action within two seconds.** The short 9:16 template follows the house editing rules: no end card, a call to action only in the last 2 s, and a title only in the first 3 s.
- Rejected: a fixed 1.5 s closing card. It would be an end card under another name and add a closing screen to every short.

**Safe zone enforced when a template is saved.** Badge, title, captions and logo must lie outside the top 14 % and bottom 35 % of the frame. A template that breaks this is refused on save, naming the slot.
- Rejected: leaving the check to each renderer. Renderers would disagree about the zone, and a short could pass in one tool and fail in another.

**Whole-kit immutable versions.** Each saved change stores the complete kit (palette, typography, marks, texts and templates) as one version record. Pinning and reading an old version then need no reconstruction.
- Rejected: per-field history with replay. Reconstructing a pinned state from deltas is error-prone and makes "version 2 still readable" depend on every later change.
- Rejected: a mutable record plus an audit log. Pinned consumers need a stable document, not a reconstruction of one.

**Tonal palette computed on the server.** Tones are derived from each seed colour by a fixed tonal-palette algorithm (Material Design 3 HCT tones), run in the server when a version is saved. Stored tones are then part of the version, so every consumer gets identical values.
- Rejected: tones typed in by hand. They drift from their seeds over time.
- Rejected: computing tones in each client. The overlay, desktop and web would each carry the algorithm and could disagree.

**Contrast gate on publication.** The server computes the WCAG contrast ratio for each text role against its background role and refuses publication below AA. Publishing is the moment the kit becomes authoritative, so the gate sits there.
- Rejected: warnings only. Text that fails contrast reaches stream overlays and thumbnails unnoticed.

**Templates as declarative documents.** A template describes elements, token references, timing, safe-zone placement and declared parameters. It contains no pixel code. The renderers interpret it. This keeps the server free of rendering (principle 2) and keeps one format for all consumers.
- Rejected: templates as images or HTML snapshots. They cannot be filled with parameters or re-themed.

**Notification carries the version number only.** The event on the SSE stream names the new version; clients read the kit they need. This matches principle 3 (resynchronise by reading state after a reconnect).
- Rejected: pushing the full kit in the event. It bloats every stream and duplicates the read path.

**Pins stored per consumer.** A consumer (an overlay scene, an OBS plugin installation, the external renderer, a desktop installation) stores its pinned version on the hub. Unpinned consumers follow the latest version.
- Rejected: pinning in the client only. A client reinstall would silently jump to the latest kit.

**Templates versioned with the kit.** A template change creates a new kit version, so one version number describes the whole look. This is an assumption made here: the brief asks for versioning of the kit and of templates together but does not say whether they version separately.

## Risks / Trade-offs

- [Risk] Overlay browser sources cache old theme values → Mitigation: the theme request includes the version number, so a new version is a new URL.
- [Risk] Tonal palette algorithm output changes between releases, shifting colours → Mitigation: the derived tones are stored in each version, so an existing version never changes when the algorithm is updated.
- [Risk] Template parameters grow into a second language → Mitigation: the parameters title, subtitle and image slot are required; the cta slot of the short adds the text parameter join_domain, which this change proposes explicitly and which takes the one installation-wide join domain for every channel; any further parameter needs a change proposal.
- [Risk] The external renderer and the seeded first version disagree on a colour during the transition → Mitigation: the renderer reads the render bundle only, so after the switch it has no colour of its own to disagree with.
- [Trade-off] Storing whole versions uses more space than deltas; the kit is small, so the cost is negligible compared with media.

## Migration Plan

First release of the brand kit: the initial version is seeded from the colour roles of the stream overlay theme and the feather mark (see Decisions), and the typography (font files and roles) is supplied by the marketing owner. The external renderer switches to reading the render bundle of the pinned version. No consumer exists in production before this change, so nothing else is migrated. Rollback leaves the stored versions in place and stops publication of new versions.

## Open Questions

- Should the tonal-palette algorithm be reviewed by design before the first kit is published? The stored tones make later changes safe, so this can be settled before the first publication without changing the specs.
