# Proposal

Conventional Commits: `feat(series)` (code PR). Phase: 1 (Recognition & Measurability). Depends on: `add-access-control`, `add-brand-kit`, `add-channel-connections`, `add-external-post-import`.

Strand: content.

## Why

OneLiteFeather cuts 7 to 17 shorts from every stream and posts them to YouTube, TikTok and Instagram. Each clip has its own topic and title, and nothing links the clips to each other. A viewer who meets one clip cannot tell in the first second that it comes from OLF and belongs to a series, so no clip leads to the next one, and nobody can measure which clip brings new players to the Minecraft network. A recurring series with a fixed badge, a fixed opening formula and a fixed hashtag makes the clips recognisable, and an episode number per post gives analytics a unit to group by.

## What Changes

- Editors define content series: name, badge text per language, a series colour that references a brand-kit colour token, a fixed opening formula shown with the title, a cover grid layout template, a fixed series hashtag, a target audience (`players`, `developers` or `mixed`), a preferred weekday and posting slot, and an optional playlist reference per channel.
- A series whose badge text fails WCAG AA contrast against its series colour is refused.
- An imported external post that carries a series hashtag is assigned to that series automatically. Editors can assign, reassign or explicitly mark a post "no series". A post belongs to at most one series and receives the next episode number of its series on first assignment.
- Menura reports, per channel and week, the share of published shorts that belong to a series or are marked "no series", and flags any week below 90 %.
- Menura warns when no active series targets the audience `players`, because the shorts must reach players as well as developers.
- The JSON render bundle for the external renderer contains every active series and, per channel, the call-to-action text with that channel's join host where one is configured.
- Where the platform adapter reports playlist membership, a series post that is not in its series playlist 24 hours after publication is flagged on the post. Phase 1 only reads playlists; writing to playlists comes with the phase 2 platform integrations.

Menura does not render or cut video in this change (principle 2). Until `add-local-editing-mcp` (phase 5) ships, shorts are rendered by the external renderer from the render bundle.

## Capabilities

### New Capabilities
- `content-series`: series catalogue, badge contrast check, assignment of posts, episode numbers, weekly series coverage, audience warning, render bundle contents and playlist check.

### Modified Capabilities
- None.

## Impact

- PostgreSQL (CloudNativePG, PostgreSQL 17, standard image): new tables for series, post assignments and per-series episode counters; no new database or extension.
- Server: REST endpoints for editors (write) and viewers (read); the render bundle endpoint for the external renderer, which is a machine client with its own credential.
- Web: series management and the coverage view.
- Brand kit: a series colour references a colour token of `add-brand-kit`, which moves to phase 1 with this roadmap re-cut.
- Jobs: no new scheduled job. Coverage is computed on read; the playlist check runs after collection of platform data.
- Join attribution: `add-join-attribution` reads the series assignment of posts. No edge in the other direction: the join host reaches the render bundle only where that change is available.
