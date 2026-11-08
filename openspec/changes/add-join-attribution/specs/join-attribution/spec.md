## Purpose

Join attribution estimates which shorts bring new players to the Minecraft network, without Menura learning who the players are. All players join over one shared join domain, so the address carries no channel. Instead the network proxy asks each new player once, optionally, where they found the network, and reports the answer as one of a fixed set of choices. Menura counts first joins, the stated sources and the unanswered joins, and estimates which posts preceded each first join. This capability defines the join domain, the event ingestion and its privacy refusal, the source answers, the freshness of the reads, the measured and stated figures, the estimated post attribution and its storage in analytics.

## ADDED Requirements

### Requirement: Join domain setting
(Must) The hub SHALL store exactly one join domain for the whole installation, which SHALL be a lower-case host name; only a person with the admin role SHALL set or change it.

#### Scenario: Join domain set
- **GIVEN** an admin and an installation without a join domain
- **WHEN** the admin sets the join domain `onelitefeather.net`
- **THEN** the hub stores that domain for the installation

#### Scenario: Domain not lower-case
- **GIVEN** an admin who sends the domain `OneLiteFeather.net`
- **WHEN** the admin saves it
- **THEN** the hub refuses the request with a Problem Details error and stores nothing

#### Scenario: Value that is not a host name
- **GIVEN** an admin who sends the value `https://onelitefeather.net/`
- **WHEN** the admin saves it
- **THEN** the hub refuses the request with a Problem Details error and stores nothing

#### Scenario: Domain changed
- **GIVEN** an installation whose join domain is `onelitefeather.net`
- **WHEN** an admin sets the join domain to `play.onelitefeather.net`
- **THEN** the hub replaces the stored domain, so the installation keeps exactly one, and new call-to-action texts use the new domain

#### Scenario: Person without admin role cannot set the domain
- **GIVEN** an editor or viewer who can see the installation settings
- **WHEN** that person sets the join domain
- **THEN** the hub refuses the request with 403 as a Problem Details error

### Requirement: Join event ingestion
(Must) The hub SHALL accept join events from the network proxy over an authenticated REST endpoint on `api-menura.onelitefeather.net`, each with an event id, a first-join flag, a timestamp and a pseudonym computed at the proxy with a keyed hash; the endpoint SHALL accept only calls from a machine client with its own credential, SHALL be rate-limited with 429 and Retry-After, and SHALL treat a repeated event id as the same event.

#### Scenario: Accepted join event
- **GIVEN** the proxy machine client with a valid credential and a join event with a new event id
- **WHEN** the proxy sends the event
- **THEN** the hub stores the event and answers 201

#### Scenario: Repeated event id
- **GIVEN** a join event already stored under its event id
- **WHEN** the proxy sends the same event again
- **THEN** the hub answers 200, stores nothing new and counts the join once

#### Scenario: Event id reused with different content
- **GIVEN** a stored join event and a new payload with the same event id but a different first-join flag or timestamp
- **WHEN** the proxy sends it
- **THEN** the hub refuses the payload with 409 as a Problem Details error and keeps the stored event

#### Scenario: Missing or wrong credential
- **GIVEN** a join event sent without a credential, or with a credential that is not valid
- **WHEN** the event is sent
- **THEN** the hub answers 401 as a Problem Details error and stores nothing

#### Scenario: Person token refused
- **GIVEN** a join event sent with the token of a signed-in person instead of the proxy credential
- **WHEN** the event is sent
- **THEN** the hub answers 403 as a Problem Details error and stores nothing

#### Scenario: Rate limit exceeded
- **GIVEN** the proxy machine client that has exceeded its rate limit
- **WHEN** it sends another event
- **THEN** the hub answers 429 with a Retry-After header and stores nothing

#### Scenario: Pseudonym stored, not the player
- **GIVEN** an accepted join event
- **WHEN** the stored event is read
- **THEN** it contains the pseudonym and no player UUID, name or IP address

### Requirement: Refusal of personal data
(Must) The hub SHALL refuse any join event payload that contains a player UUID, a player name or an IP address, under any field name, SHALL store nothing from a refused payload, and SHALL NOT log the refused values.

#### Scenario: Payload with a player UUID
- **GIVEN** a join event with a field `uuid` holding a player UUID
- **WHEN** the proxy sends the event
- **THEN** the hub refuses the payload with a Problem Details error that names the field, without repeating its value, and stores nothing

#### Scenario: Payload with a player name
- **GIVEN** a join event with a field `username` holding a player name
- **WHEN** the proxy sends the event
- **THEN** the hub refuses the payload with a Problem Details error and stores nothing

#### Scenario: Payload with an IP address
- **GIVEN** a join event with a field `client_address` holding an IP address
- **WHEN** the proxy sends the event
- **THEN** the hub refuses the payload with a Problem Details error and stores nothing

#### Scenario: Refused values not logged
- **GIVEN** a refused payload that contains a player name
- **WHEN** the refusal is logged and reported to Sentry
- **THEN** neither the log line, the trace nor the Sentry event contains the player name

### Requirement: Freshness of joins
(Must) The hub SHALL make every accepted join event and every accepted source answer visible in its reads within 60 seconds of acceptance.

#### Scenario: Join visible in the attribution read
- **GIVEN** a join event accepted at a given time
- **WHEN** the attribution read is requested within 60 seconds of that time
- **THEN** the read includes the join

#### Scenario: Join visible in the analytics rollup
- **GIVEN** a join event accepted at a given time
- **WHEN** the daily rollup of join counts is read within 60 seconds of that time
- **THEN** the rollup includes the join

#### Scenario: Freshness tested with an injected clock
- **GIVEN** a test with a fake clock that advances by 59 seconds after acceptance
- **WHEN** the read is made
- **THEN** the join is visible, without any wait in the test

### Requirement: Source answer
(Must) The hub SHALL accept, at most once per first join, a source answer from the network proxy that references the event id of that first join and carries one value from the fixed choices `youtube`, `tiktok`, `instagram`, `twitch`, `friends` and `other`. A first join without an accepted answer SHALL be counted as unanswered.

#### Scenario: Accepted answer
- **GIVEN** a stored first join with event id `E` at a given time and the answer `youtube`
- **WHEN** the proxy sends the answer for `E` two hours after the join
- **THEN** the hub stores the answer for that join and answers 201

#### Scenario: Repeated identical answer
- **GIVEN** a first join with the stored answer `twitch`
- **WHEN** the proxy sends the answer `twitch` for the same join again
- **THEN** the hub answers 200, stores nothing new and counts the answer once

#### Scenario: Different second answer
- **GIVEN** a first join with the stored answer `twitch`
- **WHEN** the proxy sends the answer `tiktok` for the same join
- **THEN** the hub answers 409 as a Problem Details error and keeps `twitch` as the only answer

#### Scenario: Answer for an unknown join
- **GIVEN** an answer that references an event id with no stored first join
- **WHEN** the proxy sends the answer
- **THEN** the hub answers 404 as a Problem Details error and stores nothing

### Requirement: Source answer limits
(Must) The hub SHALL accept a source answer only up to 24 hours after the timestamp of the join, SHALL refuse any value outside the fixed choices and any other field, including free text, and SHALL use the machine client, credential and rate limit of join event ingestion.

#### Scenario: Answer after 24 hours
- **GIVEN** a stored first join and an answer for it that arrives 25 hours after the join
- **WHEN** the proxy sends the answer
- **THEN** the hub refuses it with a Problem Details error and stores nothing; the join stays unanswered

#### Scenario: Unknown choice value
- **GIVEN** an answer whose value is `reddit`, which is not one of the choices
- **WHEN** the proxy sends the answer
- **THEN** the hub answers 422 as a Problem Details error and stores nothing

#### Scenario: Free text refused
- **GIVEN** an answer with an extra field `text` that holds "a friend told me"
- **WHEN** the proxy sends the answer
- **THEN** the hub answers 422 as a Problem Details error, stores nothing, and does not log the text

### Requirement: New players in total and by stated source
(Must) The hub SHALL report, per day and per calendar week, the number of first joins in total, the number of first joins per stated source, the number of unanswered first joins, and the answer rate as answered first joins divided by all first joins; a non-first join SHALL NOT be counted as a new player.

#### Scenario: Daily total
- **GIVEN** three first joins on one day and one on the next day
- **WHEN** the daily report is read
- **THEN** it shows three for the first day and one for the next

#### Scenario: Weekly total equals daily sum
- **GIVEN** first joins on the seven days of one week
- **WHEN** the weekly report is read
- **THEN** the week's total equals the sum of its daily totals

#### Scenario: Stated sources and unanswered sum to the total
- **GIVEN** five first joins: three answered `youtube`, one answered `friends` and one unanswered
- **WHEN** the report is read
- **THEN** it shows `youtube` 3, `friends` 1, unanswered 1, the total 5 and an answer rate of 80 %

#### Scenario: Non-first joins not counted
- **GIVEN** a join event whose first-join flag is false
- **WHEN** the report is read
- **THEN** the event is not counted as a new player

### Requirement: Stated and measured figures kept apart
(Must) The hub SHALL label every per-platform figure as "stated by players", SHALL never label a per-platform figure as measured by a platform, and SHALL show the answer rate next to every stated-source figure.

#### Scenario: Platform figure labelled
- **GIVEN** a stated-source figure for `tiktok`
- **WHEN** the figure is read
- **THEN** it carries the label "stated by players" and the answer rate of the report next to it

#### Scenario: No platform-measured label
- **GIVEN** any per-platform figure in the reports
- **WHEN** the figure is read
- **THEN** no label says that a platform measured it

### Requirement: Post attribution as estimate
(Must) For each first join, the hub SHALL attribute the join to the posts of all channels and platforms published within the attribution window before the join, SHALL split the join evenly across those posts, and SHALL mark every such attribution as an estimate; the window SHALL be 72 hours unless an admin sets another value for the whole installation, and a join with no post in its window SHALL NOT be attributed to any post.

#### Scenario: Even split across three posts
- **GIVEN** three posts published 10, 20 and 30 hours before a first join, on any channels
- **WHEN** the attribution runs
- **THEN** each post receives 1/3 of the join, marked as an estimate

#### Scenario: Post outside the window
- **GIVEN** a post published 80 hours before a first join and no other post in the window
- **WHEN** the attribution runs
- **THEN** the post receives nothing and the join is not attributed to any post

#### Scenario: Installation window set to 24 hours
- **GIVEN** the attribution window set by an admin to 24 hours and a post published 30 hours before a first join
- **WHEN** the attribution runs
- **THEN** the post receives nothing

#### Scenario: Post published after the join
- **GIVEN** a post published 2 hours after a first join
- **WHEN** the attribution runs
- **THEN** the post does not receive that join

#### Scenario: Overlapping windows
- **GIVEN** post A published at hour 0, a first join J1 at hour 10 that is unanswered, post B published at hour 40 and a first join J2 at hour 50 that is unanswered, on the same installation
- **WHEN** the attribution runs
- **THEN** J1 goes entirely to post A (post B did not exist yet), J2 is split evenly between posts A and B because both are inside its 72-hour window, post A receives 1.5 in total, post B receives 0.5, and the two joins are counted exactly once each

#### Scenario: Estimate marked in reads
- **GIVEN** a post with attributed first joins
- **WHEN** the post's attribution is read
- **THEN** the count is shown with the label "estimate"

#### Scenario: Attribution of an invisible post
- **GIVEN** a post whose channel the caller may not see
- **WHEN** the caller requests its attribution
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Platform restriction by stated source
(Must) Where a first join has an accepted answer naming `youtube`, `tiktok`, `instagram` or `twitch`, the hub SHALL restrict its post attribution to the posts of that platform within the attribution window; where that platform has no post in the window, the hub SHALL attribute the join to the platform only and to no post. For answers `friends` and `other`, and for unanswered joins, the hub SHALL use the all-posts split of the post attribution requirement.

#### Scenario: Answer restricts the split to its platform
- **GIVEN** a first join answered `youtube`, a YouTube post published 10 hours before and a TikTok post published 20 hours before
- **WHEN** the attribution runs
- **THEN** the YouTube post receives the whole join and the TikTok post receives nothing, marked as an estimate

#### Scenario: Platform without a post in the window
- **GIVEN** a first join answered `instagram` and only a TikTok post in the window
- **WHEN** the attribution runs
- **THEN** no post receives the join, and the join is counted for the platform `instagram` only

#### Scenario: Answer friends uses all posts
- **GIVEN** a first join answered `friends` and a YouTube post and a TikTok post published 10 and 20 hours before
- **WHEN** the attribution runs
- **THEN** each post receives half of the join, marked as an estimate

### Requirement: Series attribution
(Must) The hub SHALL sum the estimated first joins per series through the series of the posts they are attributed to, and SHALL count the estimate of a post without a series under "no series".

#### Scenario: Series sum
- **GIVEN** two posts of series A that receive 1.5 and 0.5 estimated joins
- **WHEN** the series attribution is read
- **THEN** series A shows 2.0

#### Scenario: Post without series
- **GIVEN** a post marked "no series" that receives 1 estimated join
- **WHEN** the series attribution is read
- **THEN** "no series" shows 1.0

#### Scenario: Sum equals post estimates
- **GIVEN** any set of attributed joins
- **WHEN** the series sums are compared with the post estimates
- **THEN** the sum of the series values equals the sum of the post estimates

### Requirement: Join metrics in analytics
(Must) The hub SHALL write the first-join counts as internal metric points into the analytics store of `add-content-analytics`, per day and per stated source, so that dashboards read them from rollups; unanswered joins SHALL be derived as the total minus the stated sources and SHALL NOT be stored as a separate count.

#### Scenario: Join count point stored
- **GIVEN** an accepted first join on a given day that is answered `youtube`
- **WHEN** the metric points are written
- **THEN** the analytics store holds a join count point for that day and the source `youtube`

#### Scenario: Rollup equals the joins
- **GIVEN** the join count points of one day
- **WHEN** the daily rollup is compared with the accepted first joins of that day
- **THEN** the rollup total equals the number of those joins

#### Scenario: Answer arriving later is added
- **GIVEN** a first join stored on a day and its source answer accepted later the same day
- **WHEN** the metric points are read
- **THEN** the join is counted once under its stated source and the day's total does not change

### Requirement: Call-to-action text with join domain
(Should) The hub SHALL provide one call-to-action text per language that contains the join domain, for all channels, to the render bundle of `add-content-series`.

#### Scenario: Text in two languages
- **GIVEN** the join domain `onelitefeather.net` and the languages `de` and `en`
- **WHEN** the call-to-action texts are read
- **THEN** the German text reads "Spiel mit uns: onelitefeather.net" and the English text reads "Play with us: onelitefeather.net"

#### Scenario: No join domain configured
- **GIVEN** an installation without a join domain
- **WHEN** the call-to-action texts are read
- **THEN** no text is provided and no error is raised

#### Scenario: Domain changed
- **GIVEN** a join domain that an admin changes to `play.onelitefeather.net`
- **WHEN** the call-to-action texts are read afterwards
- **THEN** the texts carry the new domain
