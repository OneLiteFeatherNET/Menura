# Tasks

## 1. Endpoint and authorization

- [ ] 1.1 Write failing tests that an MCP client with a valid token from `add-client-and-mcp-auth` completes a Streamable HTTP initialisation and lists tools on the single endpoint; verify they fail for the expected reason.
- [ ] 1.2 Implement the Streamable HTTP endpoint behind the token checks of `add-client-and-mcp-auth`; verify the tests from 1.1 pass.
- [ ] 1.3 Write failing tests that the tool list differs by role (a viewer's list has no write tool, an editor's list has the draft tool) and is derived from the roles in the MCP token; verify they fail.
- [ ] 1.4 Implement role-filtered tool listing from the roles of the MCP token; verify the tests from 1.3 pass.
- [ ] 1.5 Write failing tests that a call the role does not allow is refused without a change, that an object outside the caller's roles answers 404, and that every changing call and every refused call is audit-logged with channel "via MCP"; verify they fail.
- [ ] 1.6 Implement the per-call role check, the 404 rule and the audit entries; verify the tests from 1.5 pass.
- [ ] 1.7 Write failing tests that requests over the per-client and per-person budget get 429 with Retry-After as Problem Details; verify they fail.
- [ ] 1.8 Implement the MCP rate limits with the limits configured in the gateway; verify the tests from 1.7 pass.

## 2. Tool registration port

- [ ] 2.1 Write failing tests with a fake capability tool that a registered tool is listed only for the roles it names, is checked again on every call, is audit-logged via MCP, and has its output passed through the projection; verify they fail.
- [ ] 2.2 Implement the registration port that capability changes use to add MCP tools, and verify the tests from 2.1 pass.

## 3. Pipeline and runway

- [ ] 3.1 Write failing tests with an injected clock that the pipeline view returns counts per channel and stage (draft, in review, approved, scheduled), that the runway for three scheduled posts at one post per day on 2026-11-02 is 2026-11-05, and that a runway below the configured threshold is marked; verify they fail.
- [ ] 3.2 Implement the pipeline and runway tool on the distribution capability with the configured threshold, and verify the tests from 3.1 pass.

## 4. Distribution tools without approval

- [ ] 4.1 Write failing tests that drafts can be created and updated through MCP with the MCP client recorded as author, that the calendar can be read, and that a revision proposal with rationale is stored as a new revision that leaves the post unchanged; verify they fail.
- [ ] 4.2 Implement the draft, calendar and revision tools on the distribution capability and register them on the port of task 2.2; verify the tests from 4.1 pass.
- [ ] 4.3 Write failing tests that the tool list contains no approve, schedule-for-publication or publish tool, for any role; verify they fail.
- [ ] 4.4 Implement the surface without approval tools; verify the tests from 4.3 pass. The refusal of approval by MCP tokens is tested in `add-client-and-mcp-auth`.

## 5. Analytics tools

- [ ] 5.1 Write failing tests that the analytics tools return the same aggregates as the REST rollup endpoints and that no analytics tool changes data; verify they fail.
- [ ] 5.2 Implement the read-only analytics tools on the rollup endpoints and register them on the port of task 2.2; verify the tests from 5.1 pass.

## 6. Output safety and no video editing

- [ ] 6.1 Write failing tests that every tool and resource output is checked for channel credentials, access and refresh tokens, job lease tokens, scene tokens and viewer identifiers, using a fixture that contains those values; verify they fail.
- [ ] 6.2 Implement the shared output projection that removes credentials, tokens, lease tokens, scene tokens and viewer data from every registered tool, and verify the tests from 6.1 pass.
- [ ] 6.3 Write a failing test that no tool registered on the server renders, cuts or transcodes media, and that a registration declaring a media operation fails the build; verify it fails.
- [ ] 6.4 Implement the tool surface check in the registration port, and verify the test of 6.3 passes.

## 7. Stream delivery

- [ ] 7.1 Write failing tests with an injected clock that an open MCP stream carries a keep-alive at least every 30 seconds during a long tool call; verify they fail.
- [ ] 7.2 Emit MCP stream keep-alives, and verify the tests of 7.1 pass.

## 8. Integration checks

- [ ] 8.1 Connect Claude Code and Claude Desktop to a test hub through the authorization flow, read the pipeline, create a draft and propose a revision, and verify that no approval or publish is possible through the client; record the result in the pull request.
- [ ] 8.2 Run the server test suite under the heavy-job lock and verify it passes.

## 9. Delivery

- [ ] 9.1 Open the code pull request titled `feat(mcp): add hub mcp server`.
