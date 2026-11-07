# Design

## Context

`add-brand-kit` (Phase 1) provides the versioned kit, its publication gate, pins and the change notification. This change adds what sits on top: individual variants per person, MCP access to the brand, and the web palette editor and sub-brands. It is split from the kit so that recognition ships in Phase 1 without waiting for the hub MCP server (Phase 3) or for profiles.

Constraints from the project context: the server is the source of truth for brand data, every client holds its own SSE stream, and principle 4 keeps publication with a person.

## Goals / Non-Goals

**Goals:**
- Individual variants of the kit without copying the kit into each profile.
- One central change reaches every person at once, unless that person overrode the exact token.
- Brand resources reachable by agents through the hub MCP server, with the same version rules as REST.

**Non-Goals:**
- No free-form overrides; the kit stays the standard.
- No server-side rendering of templates (principle 2).
- No change to the publication rules of the kit; the editor only previews unpublished values.

## Decisions

**Split from the core.** Recognition (kit, templates, render bundle) ships in Phase 1 in `add-brand-kit`. Profiles, MCP resources, the palette editor and sub-brands are in Phase 5 here.
- Rejected: one change in Phase 1. It would pull the hub MCP server (Phase 3) forward into Phase 1 and hold recognition back until profiles and MCP reads were done.

**Per-person profiles as override documents.** A profile stores only the overrides, keyed by token or asset identifier, and is resolved on read against the kit version a consumer pinned. Inheritance is therefore computed, never copied, so a central change needs no per-person write and reaches everyone on their next read or notification.
- Rejected: copying the whole kit into each profile. Every central change would then need a write per person, and an override would be indistinguishable from a copied value.
- Rejected: free-form overrides of any token. The kit must stay the standard; the overridable markers are the only place where individuality is allowed.

**Profile overrides reuse the publication gate.** A text-role override runs the same WCAG AA check as the central kit, so a personal colour cannot break readability on stream.

## Migration Plan

No profiles exist before this change, so nothing is migrated. Each person's profile is created on first read with no overrides (see the brand-profiles spec). Rollback removes the profile endpoints and the MCP resources and keeps the kit untouched.
