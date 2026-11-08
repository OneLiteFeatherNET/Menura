# Proposal

Conventional Commits: `feat(brand)` (code PR). Phase: 5 (Local & Brand). Depends on: `add-brand-kit`, `add-access-control`, `add-hub-mcp-server`.

Strand: platform.

## Why

The brand kit of `add-brand-kit` gives every shorts, overlay and stream output the same look. Some streamers want an individual variant of that look, for example their own accent colour or name tag, while the organisation's standard stays in place. The web palette editor and sub-brands for projects were deferred from the kit to keep Phase 1 small; the brand resources on the hub MCP server need that server, which comes in Phase 3.

## What Changes

- Add per-person brand profiles: the kit marks which colour tokens, typography roles and assets are overridable; each person's profile overrides only those and inherits everything else, so a central change reaches every person at once unless they overrode that exact token. Overrides pass the same contrast rule as the kit.
- Add personal assets (logo, name tag, camera frame) that replace a kit asset of the same role only where the kit marks that asset overridable.
- Expose the current brand kit, its layout templates and the editing rules as hub MCP resources, including reads of a pinned version.
- Should: web palette editor with live preview of templates before publication.
- Could: sub-brands (for example per project) that inherit from the OLF base kit.
- No breaking changes: the kit and its REST reads are unchanged.

## Capabilities

### New Capabilities
- `brand-profiles`: per-person profiles, the overridable markers of the kit, inheritance, personal assets and profile access.
- `brand-mcp-resources`: current brand kit, layout templates and editing rules exposed as hub MCP resources, including reads of a pinned version.
- `brand-editing`: the web palette editor with live preview and sub-brands that inherit from the base kit.

### Modified Capabilities
- None. The capabilities of `add-brand-kit` are not changed.

## Impact

- Server: profile storage and resolution against the pinned or latest kit version, override and asset validation, profile access rules, change propagation through the existing SSE notification, brand MCP resources on the hub MCP server, sub-brand inheritance.
- Web: palette editor with live preview (Should); profile editing for each person.
- OBS plugin, overlay and desktop: read the person's resolved profile; they are covered by `obs-plugin`, `overlay-scenes` and `add-local-editing-mcp`.
- Access: owners change their own profile; editors read profiles and change the kit, as defined in `add-access-control`.
