## Purpose

Content series make the shorts of OneLiteFeather recognisable as one series: each series has a fixed badge, opening formula, hashtag and colour, each post belongs to at most one series and receives an episode number, and Menura reports how much of the published output is covered. This capability defines the series catalogue, the checks that protect legibility and reach, and what the external renderer receives. It does not render video.

## ADDED Requirements

### Requirement: Series catalogue
(Must) The hub SHALL store each content series with a name, badge text per language, a series colour that references a colour token of the brand kit, an opening formula, a cover grid layout template reference, a fixed series hashtag, a target audience of `players`, `developers` or `mixed`, a preferred weekday and posting slot, and an optional playlist reference per channel. Editors SHALL create and change series, and viewers SHALL read them.

#### Scenario: Series created with all fields
- **GIVEN** an editor with the editor role
- **WHEN** the editor creates a series with a name, badge text, a colour token, an opening formula "Kurz erklärt:", a layout template, the hashtag, the audience `developers`, a weekday and a posting slot
- **THEN** the hub stores the series and returns it with an opaque, non-sequential identifier

#### Scenario: Raw colour value refused
- **GIVEN** an editor who sends a series colour as a hex value instead of a token reference
- **WHEN** the editor saves the series
- **THEN** the hub refuses the request with a Problem Details error and stores nothing

#### Scenario: Unknown target audience refused
- **GIVEN** an editor who sends the audience `everyone`
- **WHEN** the editor saves the series
- **THEN** the hub refuses the request with a Problem Details error naming the allowed values

#### Scenario: Viewer cannot change a series
- **GIVEN** a viewer who can see a series
- **WHEN** the viewer requests a change of its name
- **THEN** the hub refuses the request with 403 as a Problem Details error and the series stays unchanged

#### Scenario: Series outside the caller's roles
- **GIVEN** a series that is visible only to a role the caller does not have
- **WHEN** the caller requests that series by its identifier
- **THEN** the hub answers 404 as a Problem Details error

#### Scenario: Viewer reads the catalogue
- **GIVEN** a viewer with the viewer role
- **WHEN** the viewer lists the series
- **THEN** the hub returns every series the viewer may see, including inactive ones marked as inactive

### Requirement: Badge contrast
(Must) The hub SHALL refuse a series whose badge text colour does not reach a contrast ratio of at least 4.5:1 against its series colour, as required for normal text by WCAG 2.2 level AA, and the refusal SHALL name the colour pair and the measured ratio.

#### Scenario: Pair meets the threshold
- **GIVEN** a badge text colour with a contrast ratio of 4.5:1 or more against the series colour
- **WHEN** the editor saves the series
- **THEN** the hub stores the series

#### Scenario: Pair below the threshold refused
- **GIVEN** a badge text colour with a contrast ratio of 3.2:1 against the series colour
- **WHEN** the editor saves the series
- **THEN** the hub refuses the request with a Problem Details error that names both colour tokens and the ratio 3.2:1, and stores nothing

#### Scenario: Colour change re-checked
- **GIVEN** a stored series whose badge text meets the threshold
- **WHEN** an editor changes the series colour so that the pair falls below the threshold
- **THEN** the hub refuses the change and the stored colour stays unchanged

### Requirement: Assignment of posts to series
(Must) When an imported external post carries the series hashtag of exactly one series, the hub SHALL assign the post to that series automatically; editors SHALL be able to assign or reassign a post to a series or mark it "no series"; and each post SHALL belong to at most one series.

#### Scenario: Automatic assignment by hashtag
- **GIVEN** a series with hashtag `#kubernetes30s` and an imported external post whose description contains that hashtag
- **WHEN** the import completes
- **THEN** the post is assigned to that series

#### Scenario: Manual reassignment
- **GIVEN** a post assigned to series A
- **WHEN** an editor reassigns it to series B
- **THEN** the post belongs to series B only and the change is recorded with the editor and the time

#### Scenario: Explicit "no series"
- **GIVEN** an imported post that carries no series hashtag
- **WHEN** an editor marks it "no series"
- **THEN** the post is marked "no series" and is counted as covered in the coverage report

#### Scenario: Two series hashtags on one post
- **GIVEN** an imported post that carries the hashtags of two series
- **WHEN** the import completes
- **THEN** the hub does not assign the post and flags it for an editor decision

#### Scenario: Second series refused
- **GIVEN** a post assigned to series A
- **WHEN** a request adds a second assignment to series B without removing the first
- **THEN** the hub refuses the request with a Problem Details error

### Requirement: Episode numbers
(Must) On the first assignment of a post to a series, the hub SHALL give the post the next sequential episode number of that series, and SHALL never reuse an episode number of a series, including after a post is reassigned or unassigned.

#### Scenario: Sequential numbers
- **GIVEN** a series whose last episode number is 4
- **WHEN** a new post is assigned to it for the first time
- **THEN** the post receives episode number 5

#### Scenario: Reassignment keeps the number
- **GIVEN** a post that received episode number 5 in series A
- **WHEN** the post is reassigned to series B and back to series A
- **THEN** the post keeps episode number 5 and no other post receives it

#### Scenario: Removed post does not free its number
- **GIVEN** a post that received episode number 6 and was later marked "no series"
- **WHEN** the next post is assigned to the same series
- **THEN** that post receives episode number 7

#### Scenario: "No series" posts have no number
- **GIVEN** a post marked "no series"
- **WHEN** the series numbers are read
- **THEN** the post has no episode number

### Requirement: Series coverage per channel and week
(Must) The hub SHALL report, per channel and calendar week, the share of published shorts that belong to a series or are marked "no series", and SHALL flag every week in which that share is below 90 %.

#### Scenario: Week at the threshold
- **GIVEN** a channel with ten published shorts in a week, nine of them covered
- **WHEN** the coverage report is read
- **THEN** the week shows 90 % and is not flagged

#### Scenario: Week below the threshold
- **GIVEN** a channel with ten published shorts in a week, eight of them covered
- **WHEN** the coverage report is read
- **THEN** the week shows 80 % and is flagged

#### Scenario: Only published shorts count
- **GIVEN** a channel with ten published shorts and two unpublished drafts, all covered
- **WHEN** the coverage report is read
- **THEN** the week shows 100 % and the drafts are not counted

#### Scenario: Week without published shorts
- **GIVEN** a channel with no published shorts in a week
- **WHEN** the coverage report is read
- **THEN** the week is reported as having no shorts and is not flagged

### Requirement: Player audience present
(Must) While the hub holds no active series with the target audience `players`, the hub SHALL show a warning that the shorts do not address players.

#### Scenario: Warning without a players series
- **GIVEN** only active series with the audience `developers` or `mixed`
- **WHEN** an editor opens the series overview
- **THEN** the overview shows the warning that no active series targets players

#### Scenario: No warning with an active players series
- **GIVEN** an active series with the audience `players`
- **WHEN** an editor opens the series overview
- **THEN** no warning is shown

#### Scenario: Inactive series does not count
- **GIVEN** a players series that is marked inactive and no other players series
- **WHEN** an editor opens the series overview
- **THEN** the warning is shown

### Requirement: Series in the render bundle
(Must) The render bundle endpoint SHALL return, for an authorised machine client of the external renderer, every active series with its badge text, series colour token, opening formula and hashtag, and, for each channel, the call-to-action text with that channel's join host where the join host is configured by `add-join-attribution`.

#### Scenario: Bundle contains active series
- **GIVEN** two active series and one inactive series
- **WHEN** the renderer requests the render bundle
- **THEN** the bundle contains the two active series with their badge text, colour token, opening formula and hashtag, and not the inactive series

#### Scenario: Call-to-action with join host
- **GIVEN** a channel whose join host is `yt.onelitefeather.net`
- **WHEN** the renderer requests the render bundle
- **THEN** the call-to-action text for that channel contains the host

#### Scenario: Channel without join host
- **GIVEN** a channel with no configured join host
- **WHEN** the renderer requests the render bundle
- **THEN** the bundle contains no join host for that channel and the call-to-action text is omitted without an error

#### Scenario: Bundle refused for a person without the machine role
- **GIVEN** a signed-in person whose token is not a renderer machine client
- **WHEN** the person requests the render bundle
- **THEN** the hub refuses the request with 403 as a Problem Details error

#### Scenario: Bundle for an unknown channel
- **GIVEN** a channel identifier that does not exist
- **WHEN** the renderer requests the bundle for that channel
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Playlist check
(Should) Where the platform adapter reports playlist membership, the hub SHALL flag a series post that is not in its series playlist 24 hours after its publication, and SHALL show the flag on the post.

#### Scenario: Post in its playlist
- **GIVEN** a series post with a playlist reference, published more than 24 hours ago and present in that playlist
- **WHEN** the playlist check runs
- **THEN** the post carries no flag

#### Scenario: Post missing from its playlist
- **GIVEN** a series post published 25 hours ago that is not in its series playlist
- **WHEN** the playlist check runs
- **THEN** the post is flagged with the playlist name

#### Scenario: Check not yet due
- **GIVEN** a series post published 23 hours ago that is not in its playlist
- **WHEN** the playlist check runs
- **THEN** the post is not flagged

#### Scenario: Platform does not report playlists
- **GIVEN** a channel whose platform adapter does not report playlist membership
- **WHEN** the playlist check runs for its series posts
- **THEN** no flag is set and the post shows that the playlist is not checked
