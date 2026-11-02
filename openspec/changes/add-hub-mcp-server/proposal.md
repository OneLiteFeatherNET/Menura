# Proposal

Conventional Commits: `feat(mcp)` (code PR). Phase: 3 (Hub-MCP). Depends on: `add-access-control`,
`add-client-and-mcp-auth`, `add-content-distribution`, `add-content-analytics`.
Strand: platform.

## Why

Claude (Claude Code and Claude Desktop) should read the team's publishing pipeline and channel analytics, and draft
and propose posts, from any PC, without holding any platform credentials and without any path to publish or approve.
A person should be able to ask Claude how many posts are still planned for each channel and when the pipeline runs
empty, and have Claude draft the next post for a human to review.

## What Changes

- A remote MCP endpoint (Streamable HTTP) for all Claude clients. Its authorization is the one of
  `add-client-and-mcp-auth` (metadata, client registration and resource-bound tokens), built on the roles and the
  authorization server of `add-access-control`.
- Every tool call is checked against the roles of the person behind the token, audit-logged as performed via MCP
  (changing calls and refused calls alike), and rate limited per client and per person. Tools a role cannot use are not
  listed for that person.
- Distribution tools: create and update drafts, read the content calendar, and propose a revision of a draft or
  scheduled post with a rationale, which is stored as a new revision that waits for a publisher. No tool approves,
  schedules for publication or publishes.
- Pipeline tool: per channel, the number of posts in each stage (draft, in review, approved, scheduled), and the runway
  forecast with the warning threshold.
- Analytics tools: read-only aggregates from the rollup endpoints of `add-content-analytics`.
- Shared output projection: no tool or resource result contains channel credentials, access or refresh tokens, job lease
  tokens, scene tokens or personal data of viewers.
- The hub never edits video: no tool renders, cuts or transcodes media, checked by a test of the tool surface.
- A registration port through which the other capability changes add their tools. Each change adds its own MCP tools
  in its own change: library tools and transcript search (`add-content-library`), media job tools
  (`add-media-job-queue`), brand resources and workflow prompts (`add-brand-kit`), the editing playbook
  (`add-local-editing-mcp`) and overlay tools (`add-overlay-system`). This change does not define them.
- Stream heartbeat: an open MCP stream carries a keep-alive at least every 30 seconds.

## Capabilities

### New Capabilities

- `hub-mcp`: the remote MCP endpoint and its transport, the role-filtered tool surface and its registration
  port, the distribution, pipeline and analytics tools, role limits, audit, rate limits, the output projection, the rule
  that no tool edits video, and stream heartbeats.

### Modified Capabilities

- None.

## Impact

- Hub: a new MCP surface next to the REST API and the backend-for-frontend, reusing the access-control model, the
  client-auth checks and the read and write paths of the distribution and analytics capabilities.
- Clients: Claude Code and Claude Desktop authorize through the flow of `add-client-and-mcp-auth`; no platform credential
  is ever sent to a client.
- Later changes: capability changes add MCP tools through the registration port, so they do not add endpoints of their own.
- The local desktop MCP (`add-local-editing-mcp`) is a separate stdio server and remains the only place where video is
  edited.
