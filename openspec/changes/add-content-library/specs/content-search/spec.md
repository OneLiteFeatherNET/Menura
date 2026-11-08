# Spec Delta

## Purpose

Lets people and MCP clients find moments in stream content by the words spoken, or by meaning, and narrow results by uploader, person, tag, stage, date and usage status, so that the right recording position is reached quickly.

## ADDED Requirements

### Requirement: Full-text transcript search

(Must) The system SHALL search transcript text with full-text matching and return each hit as a transcript segment with its raw item (stream recording) and its position in that recording.

#### Scenario: Word found in a transcript
- **GIVEN** a transcript of a raw item contains the phrase "boss fight" at 01:12:30
- **WHEN** a viewer searches for "boss fight"
- **THEN** the result contains that segment with its raw item and the position 01:12:30

#### Scenario: Newly attached transcript is searchable
- **GIVEN** a transcript has just been uploaded for a raw item
- **WHEN** a viewer searches for a word from that transcript
- **THEN** the segment with that word is returned

#### Scenario: No match
- **GIVEN** no transcript contains the word "xylophone"
- **WHEN** a viewer searches for "xylophone"
- **THEN** the result is empty and no error is returned

### Requirement: Jump to timestamp

(Must) The system SHALL return, for each search hit, the timestamp at which the matching speech starts so that a client can open the raw item at that moment.

#### Scenario: Hit opens at its start
- **GIVEN** a search hit whose speech starts at 00:42:07.300 in a raw item
- **WHEN** a client follows that hit
- **THEN** the client receives the raw item identifier and the start time 00:42:07.300

### Requirement: Filtered search

(Must) The system SHALL narrow search results by uploader ("mine" or a named member), person tag, free tag, stage, date range and usage status, using the same meaning as the pool listing filters, and SHALL apply all given filters together. A stage filter of raw keeps hits from raw items; a stage filter of cut or rendered keeps hits from raw items that have at least one clip at that stage.

#### Scenario: Combined filters
- **GIVEN** matching segments exist in two raw items, one uploaded by member A and tagged "speedrun", the other uploaded by member B
- **WHEN** a viewer searches with the uploader filter set to member A and the tag filter "speedrun"
- **THEN** only matching segments from the first raw item are returned

#### Scenario: Usage status filter
- **GIVEN** matching segments belong to raw items whose clips are either never used or already published
- **WHEN** a viewer filters for usage status "never used"
- **THEN** only segments from raw items whose clips are never used are returned

#### Scenario: Stage filter
- **GIVEN** matching segments in two raw items, one of which has a rendered clip and the other none
- **WHEN** a viewer filters for stage rendered
- **THEN** only the segments of the raw item with the rendered clip are returned

### Requirement: Semantic search over transcript segments

(Should) The system SHALL rank transcript segments by similarity of meaning to a query in addition to word matching, and SHALL return the same position and filter behaviour as full-text search.

#### Scenario: Meaning match without shared words
- **GIVEN** a segment says "the streamer finally beat the final boss" and no other segment uses the words "won" or "victory"
- **WHEN** a viewer searches semantically for "victory moment"
- **THEN** that segment is among the results, ranked by similarity, with its raw item and start time

#### Scenario: Filters apply to semantic search
- **GIVEN** a semantic query and an uploader or person filter
- **WHEN** the search runs
- **THEN** only segments that pass those filters are returned
