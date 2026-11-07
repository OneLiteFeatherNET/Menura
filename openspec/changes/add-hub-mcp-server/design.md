# Design

## Context

The hub already has the REST API and the backend-for-frontend for the web app, the access-control model with Menura's own
authorization server, roles, rate limits and the audit log (see `add-access-control`), and the MCP client flows,
metadata and approval guard (see `add-client-and-mcp-auth`). This change exposes the distribution and analytics
capabilities (`add-content-distribution`, `add-content-analytics`) through one MCP endpoint, and provides the endpoint,
the role filter, audit, limits and output projection that every later capability uses when it adds its own tools.
Requirements are in `specs/hub-mcp`; motivation is in proposal.md. Principle 4 applies: AI agents may draft and propose,
but never publish or approve. Principle 6 applies: MCP clients follow the MCP authorization specification (RFC 9728,
RFC 8414, RFC 8707). Principle 11 applies: 429 with Retry-After, 404 for objects the caller may not see, and no secrets
in output. Principle 2 applies: the hub never renders or transcodes video.

## Goals / Non-Goals

**Goals:**
- One remote MCP endpoint for all Claude clients, with the same role rules as REST.
- A registration port that later capability changes use to add their tools without changing the endpoint, its
  authorization, its audit or its output rules.
- A surface that cannot approve, publish, edit video or expose secrets, by construction and by test.

**Non-Goals:**
- The local desktop MCP (`add-local-editing-mcp`) is a separate server; this one never edits video, renders or transcodes.
- No approval or publishing tools, in any role.
- The tools of library, media jobs, brand kit, editing playbook and overlay. Each of these changes adds its own tools
  through the registration port; this change does not define them.
- No new storage: the server reads and writes through the distribution and analytics capabilities only.

## Decisions

**Single Streamable HTTP endpoint with standard authorization.** Clients connect once; the token decides which tools
exist for the person. Alternative: one MCP server per capability (rejected: each would repeat
authorization and audit, and the client would need several connections for one workflow). Alternative: reusing the web
app's session (rejected: principle 6 forbids tokens in the browser and MCP clients are not browsers).

**Tool registration port for capability changes.** A capability registers each tool with a name, the roles that may use
it, its input and output view models, and its audit label. The endpoint then applies the role filter, the per-call
check, the audit entry, the rate limit and the output projection to every registered tool in the same way. Alternative:
each capability adds its own endpoint (rejected: repeats the authorization, audit and limit rules in each change and
multiplies client connections). Alternative: a generic tool that forwards calls to REST (rejected: hides the role model
and the view models from the tests).

**Tool list filtered by role and checked again per call.** Tools a role cannot use are not listed, and every call is
checked again, so a stale client cannot call a tool it was not offered. Alternative: listing everything and refusing
calls (rejected: clients would propose actions that cannot succeed).

**No approval tools at all.** Approval is a human action in the web app; the MCP surface omits the tool instead of
refusing it. The publisher check of `add-client-and-mcp-auth` stays in force for any approval operation. Alternative: an
approval tool that refuses for MCP tokens (rejected: a single misconfigured role would then expose it; absence is the
stronger guarantee).

**Revisions as proposals, not edits.** A revision proposal with a rationale is stored as a new revision that waits for a
publisher's decision, and the post itself is left as it was. Alternative: letting the agent edit the scheduled post
(rejected: a published-looking post could change without a person's approval).

**Results are filtered by a shared projection.** Tools return view models that omit credentials, tokens, lease tokens,
scene tokens and viewer identifiers; tests check every tool's output for those fields. Alternative: filtering each tool by
hand (rejected: easy to miss a new tool).

**Analytics read rollups only.** Analytics tools call the rollup endpoints of `add-content-analytics`, so the answers
match the dashboard and no analytics tool can write. Alternative: raw points at query time (rejected: the rollups exist
for this read path).

**Pipeline runway from stored stages.** The runway is the date on which the last scheduled post at the channel's cadence
is published, computed with an injected clock; a runway below the configured threshold is marked. Alternative: a
separate forecast service (rejected: no new storage and no new component).

## Risks / Trade-offs

- [Risk] A new tool registered by a later change leaks credentials or viewer data. → Mitigation: the shared projection and
  the per-tool output test in the task list apply to every registered tool.
- [Risk] A later change registers a tool that edits media. → Mitigation: the surface check in the task list fails the
  build when a registered tool is marked as a media operation.
- [Risk] A client floods the endpoint. → Mitigation: per-client and per-person rate limits with 429 and Retry-After.
- [Risk] Clients call analytics reads repeatedly. → Mitigation: analytics tools return bounded result sets with paging.
- [Trade-off] Hiding tools per role means a client sees a different tool list per person; accepted because it matches the
  human role model.

## Open Questions

None that change specs, approach or tasks.
