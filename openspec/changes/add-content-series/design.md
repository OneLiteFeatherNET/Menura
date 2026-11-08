# Design

## Context

External posts and their channels live in PostgreSQL (see `add-external-post-import`); channels, platform connections and the playlist membership reported by the adapter port come from `add-channel-connections`. Series colours reference colour tokens of the brand kit (`add-brand-kit`), and the access roles come from `add-access-control`. Requirements are in `specs/content-series`; motivation is in proposal.md.

Principle 2 applies: Menura does not cut or render video. The external renderer (the current cut tool, outside Menura) reads a JSON render bundle from Menura, and Menura owns everything the renderer needs to know about a series. House editing rules stay with the renderer: title only in the first 3 seconds, call-to-action overlay only in the last 2 seconds, no end card, 2 to 3 hashtags, nothing in the top 14 % or bottom 35 % of the frame, and posting slots 12, 18 and 21 h assigned at random.

## Goals / Non-Goals

**Goals:**
- Every short carries a recognisable series marker: badge, opening formula and hashtag.
- Every published short is either in a series or explicitly "no series", so coverage can be measured.
- Episode numbers that are stable and never reused.
- A render bundle that the external renderer can consume without knowing the database.
- Warnings when the output stops addressing players.

**Non-Goals:**
- No video rendering, cutting, transcription or overlay in Menura (principle 2).
- No writing to playlists in phase 1.
- No join attribution in this change; the join domain is only passed through to the bundle (see `add-join-attribution`).
- No voice cloning or intro audio. An intro with a cloned voice is a possible later option and is not in scope.
- No decision on which recognition effect the series has. That is a hypothesis to test, not an acceptance criterion (see Risks).

## Decisions

**Series hashtag as the automatic assignment key.** An imported post is assigned to a series when its description contains exactly that series' fixed hashtag. The hashtag is set once by the editor and is also one of the 2 to 3 hashtags the renderer puts on the short, so the same string links the clip, the post and the series. Alternatives considered:
- Parsing the title prefix such as "Kurz erklärt:" (rejected: titles are free text, are edited after upload and differ per language; a prefix match breaks on the first typo).
- Manual assignment only (rejected: editors forget, coverage falls below 90 % without anyone noticing, and the episode numbers depend on memory).

**Badge shown for the whole duration in the safe zone.** The badge stays on screen for the full length of the short, placed inside the safe zone. A viewer who meets a clip late still sees which series it belongs to. Alternative: badge only during the title (rejected: the title is limited to 3 seconds by the house rules, which is too short to be read as a series marker).

**Series colour as a token reference, not a raw colour.** A series colour is a reference to a brand-kit token, so a central change to the palette reaches every series and the renderer. Contrast of the badge text is checked against the resolved colour at save time and again when either colour changes. Alternative: a raw hex value per series (rejected: it bypasses the brand kit and cannot be checked centrally).

**Badge contrast threshold 4.5:1.** The badge text is small and must be legible on a phone, so the threshold is the WCAG 2.2 level AA value for normal text, not the 3:1 value for large text.

**Phase 1 reads playlists only.** The playlist check reads membership that the platform adapter reports. Adding a post to a playlist is a write to the platform and comes with the phase 2 platform integrations, which carry the approval flow of principle 4. Alternative: write the playlist membership now (rejected: a platform write without the approval flow).

**Episode numbers from a per-series counter.** The counter only increases. A number is assigned on the first assignment and kept with the post, so a reassignment or a "no series" mark does not change or reuse it. Alternative: computing the number from the current list of posts (rejected: removing a post would renumber later posts and make the number unstable).

**Coverage counted per channel and week over published shorts.** Drafts and unpublished shorts are excluded, so the report measures what viewers see. Alternative: coverage over all imported posts (rejected: imports include long videos and old posts that are not shorts).

**Render bundle as a machine-client endpoint.** The renderer is a machine client with its own credential, which is the same pattern as the proxy in `add-join-attribution`. The bundle contains only what the renderer needs. Alternative: the renderer reading the database directly (rejected: a second consumer of the schema, and no access control on the rows).

**Opaque identifiers for series and episodes.** Series and post identifiers are non-sequential (RFC 9562), so episode numbers are the only sequence a viewer sees, and they are shown as a number, not as the identifier.

## Initial data (not requirements)

The first series to load into the catalogue, to be confirmed by the maintainer:
- "Kubernetes in 30 s", audience `developers`.
- "Live-Migration", audience `developers`.
- "Wir bauen OneLiteFeather", audience `mixed`.
- A Survival series for players. Its public name is still open (see open questions).

## Risks / Trade-offs

- [Risk] Recognition does not improve. → This change is judged only after about 30 posts per series have been published and measured through `add-content-analytics`. It is a hypothesis that the series makes shorts recognisable and leads to new players. It is not an acceptance criterion of this change.
- [Risk] The hashtag appears in unrelated posts and assigns them wrongly. → Editors can reassign any post, and the import flags posts with two series hashtags instead of assigning them.
- [Risk] Coverage is met by marking everything "no series". → The coverage report shows the "no series" share separately, so the marking is visible.
- [Risk] The renderer does not apply the badge contrast rule. → The contrast is checked in Menura before a series is stored, so the bundle carries only pairs that passed.
- [Risk] Series are created for players only in name. → The audience warning and the coverage report make the imbalance visible.
- [Trade-off] The preferred posting slot is a hint, not a fixed slot. The house rule assigns slots at random among 12, 18 and 21 h so that the comparison per slot is unbiased; a preferred slot would bias it. The preferred slot is shown to editors and used for planning proposals only.

## Open Questions

- **Public name of the Survival series for players.** Open. The series stays out of the initial data until the name is decided.
- **Preferred posting slot.** The maintainer confirms that the preferred slot is a hint for planning and that the random slot assignment stays the rule for comparisons.
- **Join domain in the render bundle.** The bundle carries the join domain only where `add-join-attribution` is available. There is no dependency edge from this change to that one, so the two changes do not form a cycle.
