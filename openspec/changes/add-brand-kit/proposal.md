# Proposal

Conventional Commits: `feat(brand)` (code PR). Phase: 5 (Local & Brand). Depends on: `add-content-analytics`, `add-access-control`, `add-hub-mcp-server`.

Strand: platform.

## Why

Every stream, short, thumbnail, post and overlay of OneLiteFeather should look and sound like the same organisation. Today colours, fonts and texts live in people's heads and in individual files, so the overlay, the desktop renderer and the marketing output drift apart. One central, versioned brand kit with layout templates gives all consumers the same source.

## What Changes

- Add one central brand kit, versioned: named colour tokens with light and dark roles, a tonal palette derived from seed colours, contrast checks for text roles against WCAG AA, typography (font files and roles), logos and marks with variants and safe areas, voice and tone rules, and hashtags and end-card texts per language.
- Add layout templates for short 9:16, long 16:9 intro and outro, thumbnail, social post image, end card and overlay theme. Templates reference brand tokens only, never raw colours, and declare their parameters (title, subtitle, image slot).
- Make the kit and templates readable over REST, MCP, by the overlay (theme), by the OBS plugin (scene sync) and by the desktop app (rendering). Any change creates a new version; consumers can pin a version; a new published version notifies subscribers over Server-Sent Events.
- Add per-person brand profiles: the kit marks which tokens, typography roles and assets are overridable; each person's profile overrides only those and inherits everything else, so a central change reaches every person at once unless they overrode that exact token. Overrides pass the same contrast rule as the kit.
- Should: web editor for the palette with live preview of templates, and export of the kit as design tokens (JSON and CSS variables).
- Could: sub-brands (for example per project) that inherit from the OLF base kit.
- No breaking changes: this is the first brand kit.

## Capabilities

### New Capabilities
- `brand-kit`: the versioned central kit (colours, typography, marks, text rules), its access, version pinning, change notification and token export.
- `layout-templates`: the layout templates for each output format and the overlay theme, their parameters and their use of brand tokens.
- `brand-profiles`: per-person profiles, the overridable markers of the kit, inheritance, personal assets and profile access.
- `brand-mcp-resources`: current brand kit, layout templates and editing rules exposed as hub MCP resources, including reads of a pinned version.

### Modified Capabilities
- None.

## Impact

- Server: versioned kit storage with immutable versions, contrast validation on publication, template registry, REST and MCP reads, SSE notification on new versions.
- Web: palette editor and template preview (Should).
- Overlay, OBS plugin and desktop: read the pinned or latest theme, templates and the person's resolved profile; they implement their own rendering and are covered by `overlay-scenes`, `obs-plugin` and `add-local-editing-mcp`.
- Access: editing the kit requires the editor role defined in `add-access-control`; reading is open to all signed-in roles.
