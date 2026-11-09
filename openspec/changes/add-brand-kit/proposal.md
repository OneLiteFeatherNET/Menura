# Proposal

Conventional Commits: `feat(brand)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-access-control`.

Strand: platform.

## Why

OneLiteFeather's shorts are the first thing most viewers see of the organisation: seven to seventeen per stream, on YouTube, TikTok and Instagram. A viewer has about one second to recognise the channel and to see that a clip belongs to a series. Today the look is copied by hand into the cut tool, and that copy has already drifted from the stream overlay. One central, versioned brand kit with layout templates gives the renderer and the overlay the same source, so the first second looks the same everywhere and a change is made once.

## What Changes

- Add one central brand kit, versioned: named colour tokens with light and dark roles, a tonal palette derived from seed colours, contrast checks for text roles against WCAG AA, typography (font files and roles), logos and marks with variants and safe areas, voice and tone rules, and hashtags and end-card texts per language.
- Seed the first published version from the colour roles of the stream overlay theme, so the look the stream already shows becomes the authoritative kit; the feather mark follows in a later version once the logo is uploaded as a library asset.
- Add layout templates for short 9:16, long 16:9 intro and outro, thumbnail, social post image, end card and overlay theme. Templates reference brand tokens only, never raw colours, and declare their parameters (title, subtitle, image slot). The short 9:16 template declares the slots badge, title, captions, session tiles and call to action with their timing, and keeps badge, title, captions and logo out of the safe zone.
- Make the kit and templates readable over REST, by the overlay (theme) and by the OBS plugin (scene sync). Until `add-local-editing-mcp` (Phase 5) ships, shorts are rendered by an external renderer outside Menura, which reads a JSON render bundle of a named kit version; the bundle is also exported as CSS custom properties.
- Any change creates a new version; consumers can pin a version; a new published version notifies subscribers over Server-Sent Events.
- Should: web template preview with sample parameters.
- No breaking changes: this is the first brand kit.

Per-person profiles, the web palette editor, sub-brands and the brand MCP resources move to `add-brand-profiles` (Phase 5).

## Capabilities

### New Capabilities
- `brand-kit`: the versioned central kit (colours, typography, marks, text rules), its access, version pinning, change notification, the render bundle and token export, and the initial version seeded from the overlay theme.
- `layout-templates`: the layout templates for each output format and the overlay theme, the short 9:16 slots and timing, their parameters, safe zone and their use of brand tokens.

### Modified Capabilities
- None.

## Impact

- Server: versioned kit storage with immutable versions, contrast validation on publication, seeding of the first version, template registry with token and safe-zone validation, REST reads, render bundle and CSS export, SSE notification on new versions.
- Web: template preview (Should).
- Overlay and OBS plugin: read the pinned or latest theme and templates over REST. The external renderer reads the render bundle until `add-local-editing-mcp` takes over rendering. These consumers are covered by `overlay-scenes`, `obs-plugin` and `add-local-editing-mcp`.
- Access: editing the kit requires the editor role defined in `add-access-control`; reading is open to all signed-in roles.
