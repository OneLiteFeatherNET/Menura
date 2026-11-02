# Spec Delta

## Purpose

Lets people and MCP clients find moments in stream content by the words spoken, or by meaning, and narrow results by stream, date, tags, markers and usage status, so that the right recording position is reached quickly.

## ADDED Requirements

### Requirement: Full-text transcript search

(Must) The system SHALL search transcript text with full-text matching and return each hit as a transcript segment with its recording and its position in that recording.

#### Scenario: Word found in a transcript
- **GIVEN** a transcript of a recording contains the phrase "boss fight" at 01:12:30
- **WHEN** a viewer searches for "boss fight"
- **THEN** the result contains that segment with its recording and the position 01:12:30

#### Scenario: Newly attached transcript is searchable
- **GIVEN** a transcript has just been attached to a recording
- **WHEN** a viewer searches for a word from that transcript
- **THEN** the segment with that word is returned

#### Scenario: No match
- **GIVEN** no transcript contains the word "xylophone"
- **WHEN** a viewer searches for "xylophone"
- **THEN** the result is empty and no error is returned

### Requirement: Jump to timestamp

(Must) The system SHALL return, for each search hit, the timestamp at which the matching speech starts so that a client can open the recording at that moment.

#### Scenario: Hit opens at its start
- **GIVEN** a search hit whose speech starts at 00:42:07.300 in a recording
- **WHEN** a client follows that hit
- **THEN** the client receives the recording identifier and the start time 00:42:07.300

### Requirement: Filtered search

(Must) The system SHALL narrow search results by date range, stream, tags, markers and usage status, and SHALL apply all given filters together.

#### Scenario: Combined filters
- **GIVEN** matching segments exist in two streams, one of them tagged "speedrun" and the other not
- **WHEN** a viewer searches with the stream filter set to the first stream and the tag filter "speedrun"
- **THEN** only matching segments from that stream and carrying that tag are returned

#### Scenario: Usage status filter
- **GIVEN** matching segments belong to recordings whose clips are either never used or already published
- **WHEN** a viewer filters for usage status "never used"
- **THEN** only segments from recordings whose clips are never used are returned

#### Scenario: Marker filter
- **GIVEN** a recording with a "highlight here" marker at 00:10:00 and matching speech near it
- **WHEN** a viewer filters for segments near markers
- **THEN** the matching segment near that marker is returned

### Requirement: Semantic search over transcript segments

(Should) The system SHALL rank transcript segments by similarity of meaning to a query in addition to word matching, and SHALL return the same position and filter behaviour as full-text search.

#### Scenario: Meaning match without shared words
- **GIVEN** a segment says "the streamer finally beat the final boss" and no other segment uses the words "won" or "victory"
- **WHEN** a viewer searches semantically for "victory moment"
- **THEN** that segment is among the results, ranked by similarity, with its recording and start time

#### Scenario: Filters apply to semantic search
- **GIVEN** a semantic query and a stream filter
- **WHEN** the search runs
- **THEN** only segments from that stream are returned
