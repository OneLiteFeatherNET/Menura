# library-mcp-tools

## Purpose

MCP tools and prompts of the hub MCP that work on the content library.

## ADDED Requirements

### Requirement: Library tools
(Must) The hub SHALL offer MCP tools to search the library, read a transcript range, create or suggest clips, and read the lineage of a content item, each within the caller's roles.

#### Scenario: Transcript range read
- **GIVEN** a recording with a transcript that the caller may read
- **WHEN** the client reads the transcript between two timestamps
- **THEN** the hub returns the segments in that range with their timestamps

#### Scenario: Clip suggestion
- **GIVEN** an editor who asks Claude to suggest clips from a recording
- **WHEN** the client creates clip suggestions
- **THEN** the hub stores the clips with status "suggested"

#### Scenario: Lineage read
- **GIVEN** a finished asset made from a timeline and clips
- **WHEN** the client reads its lineage
- **THEN** the hub returns the timeline, the clips and the recordings the asset came from

### Requirement: Transcripts searchable for all team content within roles
(Must) The hub SHALL make transcripts of content from any team member searchable and readable through MCP, limited to what the caller's roles allow.

#### Scenario: Search across team content
- **GIVEN** transcripts of recordings contributed by two different team members
- **WHEN** an editor searches for a phrase through MCP
- **THEN** the hub returns the matches from both contributors' content that the editor may read

### Requirement: Resources and prompts for common workflows
(Should) The hub SHALL offer MCP prompts and resources for common workflows, including finding five short candidates in the last stream.

#### Scenario: Short candidates prompt
- **GIVEN** a client that requests the short-candidates prompt for the last stream
- **WHEN** the client runs the prompt
- **THEN** the prompt guides Claude to search the transcripts of that stream and to suggest up to five clips
