## Purpose

The hub MCP server gives Claude clients one remote endpoint to the team's distribution pipeline and channel analytics from any PC, under the roles of the person behind the client. Clients authorize through the flow of `add-client-and-mcp-auth` and never hold platform credentials. This capability defines the endpoint and its transport, the role-filtered tool surface and its registration port, the distribution, pipeline and analytics tools, and what a client may never do or receive. Tools of other capabilities are added through the registration port.

## ADDED Requirements

### Requirement: Remote MCP endpoint over Streamable HTTP
(Must) The hub SHALL serve its MCP tools at one remote endpoint over Streamable HTTP, and SHALL answer requests to that endpoint only after the authorization of `add-client-and-mcp-auth` has accepted the token.

#### Scenario: Client connects with a valid token
- **GIVEN** a Claude client that has completed the authorization flow of `add-client-and-mcp-auth` for a person with the editor role
- **WHEN** the client lists the available tools over Streamable HTTP
- **THEN** the hub returns the tools that the editor role allows

### Requirement: Tool calls respect roles
(Must) Every MCP tool call SHALL be checked against the roles of the person behind the token, and a call the roles do not allow SHALL be refused without any change.

#### Scenario: Viewer calls a write tool
- **GIVEN** a token of a person with the viewer role
- **WHEN** the client calls a tool that creates a draft
- **THEN** the hub refuses the call and creates no draft

#### Scenario: Tool list follows roles
- **GIVEN** a token of a person with the viewer role
- **WHEN** the client lists the available tools
- **THEN** the list contains no tool that changes content, schedules, scenes or jobs

#### Scenario: Object outside the caller's roles
- **GIVEN** a recording that the caller may not see
- **WHEN** the client reads that recording through a tool
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Every MCP call is audit-logged
(Must) The hub SHALL audit-log every MCP tool call that changes data with the person, the tool, the affected item, the time and the channel "via MCP", and SHALL audit-log refused calls.

#### Scenario: Draft created through MCP
- **GIVEN** a token of a person with the editor role
- **WHEN** the client creates a draft post
- **THEN** the audit log records the person, the draft tool, the new post, the time and the channel "via MCP"

#### Scenario: Refused call is logged
- **GIVEN** a token of a person with the viewer role
- **WHEN** the client calls a write tool
- **THEN** the audit log records the refused call with the person and the tool

### Requirement: Pipeline and runway view
(Must) The hub SHALL offer an MCP tool that returns, per channel, the number of posts in each pipeline stage (draft, in review, approved, scheduled) and the runway forecast with the expected date the pipeline runs empty and whether the configured warning threshold is reached.

#### Scenario: Runway read for a channel
- **GIVEN** today is 2026-11-02, a channel with three scheduled posts and a posting cadence of one post per day
- **WHEN** the client reads the pipeline and runway view
- **THEN** the result lists the counts per stage and the expected empty date 2026-11-05

#### Scenario: Warning below threshold
- **GIVEN** a configured warning threshold of seven days and a channel whose runway is five days
- **WHEN** the client reads the view
- **THEN** the result marks that channel with a runway warning

### Requirement: Hub never edits video
(Must) The hub MCP SHALL NOT offer any tool that cuts, renders, transcodes or otherwise edits video.

#### Scenario: Tool list has no editing tool
- **GIVEN** any authenticated client
- **WHEN** the client lists the available tools
- **THEN** no tool renders, cuts or transcodes media

### Requirement: Distribution tools without approval
(Must) The hub SHALL offer MCP tools to create and update drafts, read the content calendar and propose revisions of a draft or scheduled post with a rationale, and SHALL NOT offer any tool that approves, schedules for publication or publishes a post.

#### Scenario: Revision proposal
- **GIVEN** a scheduled post and an editor's client
- **WHEN** the client proposes a revision with a new hashtag set and a rationale
- **THEN** the hub stores the proposal as a new revision that waits for a publisher's decision
- **AND** the post itself stays unchanged

#### Scenario: Approval is impossible through MCP
- **GIVEN** a token of a person with the publisher role
- **WHEN** the client tries to approve a post through any tool
- **THEN** no such tool exists and the approval is not performed

### Requirement: Analytics tools are read-only
(Must) The hub SHALL offer MCP tools that read analytics aggregates from rollups and SHALL NOT offer tools that change analytics data.

#### Scenario: Aggregate read for optimisation
- **GIVEN** an editor's client
- **WHEN** the client reads the aggregates of posts by channel and posting hour
- **THEN** the hub returns the aggregates within the caller's roles

### Requirement: Results contain no secrets or viewer data
(Must) No MCP tool result or resource SHALL contain channel credentials, access or refresh tokens, job lease tokens, scene tokens, or personal data of viewers.

#### Scenario: Channel details requested
- **GIVEN** a channel with stored credentials
- **WHEN** a client reads the channel through any tool
- **THEN** the result contains the channel name and status but no credential or token value

#### Scenario: Chat data requested
- **GIVEN** a recording with chat events
- **WHEN** a client reads the transcript or the lineage of its clips
- **THEN** the result contains no viewer names or identifiers from the chat

#### Scenario: Channel token requested
- **GIVEN** a client that reads the connection of a channel whose platform account holds a channel token
- **WHEN** the result is returned
- **THEN** the result contains the connection status and no channel token value

### Requirement: Rate limits on the MCP endpoint
(Must) The hub SHALL limit MCP requests per client and per person, and SHALL answer requests over the limit with 429 and a Retry-After header as a Problem Details error.

#### Scenario: Client exceeds its limit
- **GIVEN** a client that has used its request budget for the current window
- **WHEN** it sends another request
- **THEN** the hub answers 429 with a Retry-After header as a Problem Details error

### Requirement: Stream heartbeat below the proxy idle timeout
(Must) While an MCP response or notification stream is open, the hub SHALL send a keep-alive at least every 30 seconds, so that the CDN proxy in front of the hub never closes it for being idle.

#### Scenario: Long-running tool call
- **GIVEN** an MCP client waiting on a tool call that takes 3 minutes
- **WHEN** no result data is ready yet
- **THEN** the stream carries a keep-alive at least every 30 seconds and the result arrives on the same stream
