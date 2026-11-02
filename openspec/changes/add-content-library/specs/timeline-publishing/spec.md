# Spec Delta

## Purpose

Turns the timeline a finished asset was cut from into the publishing data of its posts, starting with YouTube chapter lines, so that the chapters of a video match the cut it was rendered from.

## ADDED Requirements

### Requirement: YouTube chapters from timeline marks

(Must) The system SHALL store optional chapter marks, each a time and a title, on a timeline, SHALL refuse a timeline whose marks are not in ascending order, do not start at 00:00 or are less than ten seconds apart, and SHALL add one chapter line per mark, in ascending order, to the YouTube description of each post whose finished asset was made from that timeline.

#### Scenario: Chapter lines generated
- **GIVEN** a long-form post whose finished asset was made from a timeline with chapter marks at 00:00, 01:30 and 05:00
- **WHEN** the YouTube description of the post is prepared
- **THEN** the description contains three chapter lines in that order, the first starting at 00:00

#### Scenario: Invalid marks refused
- **GIVEN** a timeline with two chapter marks nine seconds apart
- **WHEN** the timeline is uploaded
- **THEN** the system refuses the upload and names the chapter problem

#### Scenario: Timeline without marks
- **GIVEN** a post whose finished asset was made from a timeline with no chapter marks
- **WHEN** the YouTube description of the post is prepared
- **THEN** the description receives no chapter lines
