## Purpose

Join attribution estimates which shorts bring new players to the Minecraft network, without Menura learning who the players are. Each channel has its own join host, the network proxy reports first joins with a pseudonym computed at the proxy, and Menura attributes each first join to the posts of the channel that preceded it, as an estimate. This capability defines the join hosts, the event ingestion and its privacy refusal, the freshness of the reads, the attribution rules and their storage in analytics.

## ADDED Requirements

### Requirement: Join host per channel
(Must) Each connected channel SHALL have at most one join host, which SHALL be a lower-case DNS name under the configured base domain, unique across all channels; editors SHALL set and change the join host of a channel.

#### Scenario: Join host set
- **GIVEN** an editor and a connected channel without a join host
- **WHEN** the editor sets the join host `yt.onelitefeather.net` under the base domain `onelitefeather.net`
- **THEN** the hub stores the join host for that channel

#### Scenario: Host already used by another channel
- **GIVEN** a channel whose join host is `yt.onelitefeather.net`
- **WHEN** an editor sets the same host on another channel
- **THEN** the hub refuses the request with 409 as a Problem Details error and stores nothing

#### Scenario: Host not lower-case
- **GIVEN** an editor who sends the host `YT.OneLiteFeather.net`
- **WHEN** the editor saves it
- **THEN** the hub refuses the request with a Problem Details error and stores nothing

#### Scenario: Host outside the base domain
- **GIVEN** an editor who sends the host `yt.example.org`
- **WHEN** the editor saves it
- **THEN** the hub refuses the request with a Problem Details error naming the base domain

#### Scenario: Second host for one channel
- **GIVEN** a channel that already has a join host
- **WHEN** an editor sets a different host on the same channel
- **THEN** the hub replaces the host, so the channel keeps exactly one

#### Scenario: Viewer cannot set a host
- **GIVEN** a viewer who can see a channel
- **WHEN** the viewer sets its join host
- **THEN** the hub refuses the request with 403 as a Problem Details error

#### Scenario: Host of an invisible channel
- **GIVEN** a channel the caller may not see
- **WHEN** the caller requests its join host
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Join event ingestion
(Must) The hub SHALL accept join events from the network proxy over an authenticated REST endpoint on `api-menura.onelitefeather.net`, each with an event id, the join host as typed by the player, a first-join flag, a timestamp and a pseudonym computed at the proxy with a keyed hash; the endpoint SHALL accept only calls from a machine client with its own credential, SHALL be rate-limited with 429 and Retry-After, and SHALL treat a repeated event id as the same event.

#### Scenario: Accepted join event
- **GIVEN** the proxy machine client with a valid credential and a join event with a new event id
- **WHEN** the proxy sends the event
- **THEN** the hub stores the event and answers 201

#### Scenario: Repeated event id
- **GIVEN** a join event already stored under its event id
- **WHEN** the proxy sends the same event again
- **THEN** the hub answers 200, stores nothing new and counts the join once

#### Scenario: Event id reused with different content
- **GIVEN** a stored join event and a new payload with the same event id but a different join host
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
(Must) The hub SHALL make every accepted join event visible in its reads within 60 seconds of acceptance.

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

### Requirement: Unknown hosts count as direct
(Must) The hub SHALL normalise every reported join host by converting it to lower case and removing a trailing dot and a port, and SHALL count a join whose normalised host belongs to no channel as "direct".

#### Scenario: Host normalised to a channel
- **GIVEN** a channel with join host `yt.onelitefeather.net`
- **WHEN** the proxy reports the host `YT.OneLiteFeather.net:25565.`
- **THEN** the join is attributed to that channel

#### Scenario: Unknown host counts as direct
- **GIVEN** no channel with the host `twitch.onelitefeather.net`
- **WHEN** the proxy reports that host
- **THEN** the join is counted as "direct"

#### Scenario: Plain domain counts as direct
- **GIVEN** a player who types the plain domain `onelitefeather.net`
- **WHEN** the proxy reports it
- **THEN** the join is counted as "direct"

#### Scenario: Malformed host counts as direct
- **GIVEN** a reported host that is not a valid DNS name after normalisation
- **WHEN** the proxy reports it
- **THEN** the join is counted as "direct" and the event is accepted

### Requirement: New players per channel and platform
(Must) The hub SHALL report the first joins per channel and platform per day and per week, and SHALL report the direct first joins separately.

#### Scenario: Daily count per channel
- **GIVEN** three first joins through a channel's host and one on the next day
- **WHEN** the daily report for the channel is read
- **THEN** it shows three for the first day and one for the next

#### Scenario: Weekly count equals daily sum
- **GIVEN** first joins on the seven days of one week
- **WHEN** the weekly report is read
- **THEN** the week's count equals the sum of its daily counts

#### Scenario: Direct joins shown separately
- **GIVEN** two first joins through unknown hosts and one through a channel host
- **WHEN** the report is read
- **THEN** the direct line shows two and the channel line shows one

#### Scenario: Non-first joins not counted
- **GIVEN** a join event whose first-join flag is false
- **WHEN** the report is read
- **THEN** the event is not counted as a new player

### Requirement: Post attribution as estimate
(Must) For each first join through a channel's join host, the hub SHALL attribute the join to the posts of that channel published within the attribution window before the join, the window being 72 hours unless the channel sets another value, SHALL split the join evenly across those posts, and SHALL mark every such attribution as an estimate; a join with no post in its window SHALL stay attributed to the channel only.

#### Scenario: Even split across three posts
- **GIVEN** three posts of a channel published 10, 20 and 30 hours before a first join
- **WHEN** the attribution runs
- **THEN** each post receives 1/3 of the join, marked as an estimate

#### Scenario: Post outside the window
- **GIVEN** a post published 80 hours before a first join and no other post in the window
- **WHEN** the attribution runs
- **THEN** the post receives nothing and the join stays attributed to the channel only

#### Scenario: Per-channel window
- **GIVEN** a channel whose attribution window is set to 24 hours and a post published 30 hours before a first join
- **WHEN** the attribution runs
- **THEN** the post receives nothing

#### Scenario: Post published after the join
- **GIVEN** a post published 2 hours after a first join
- **WHEN** the attribution runs
- **THEN** the post does not receive that join

#### Scenario: Overlapping windows
- **GIVEN** post A published at hour 0, a first join J1 at hour 10, post B published at hour 40 and a first join J2 at hour 50, all through the same channel host
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
(Must) The hub SHALL write the join counts as internal metric points into the analytics store of `add-content-analytics`, per channel, platform and day, so that dashboards read them from rollups.

#### Scenario: Join count point stored
- **GIVEN** an accepted first join through a channel's host on a given day
- **WHEN** the metric points are written
- **THEN** the analytics store holds a join count point for that channel, platform and day

#### Scenario: Rollup equals the joins
- **GIVEN** the join count points of one day
- **WHEN** the daily rollup is compared with the accepted first joins of that day
- **THEN** the rollup value equals the number of those joins

#### Scenario: Direct joins as their own channel line
- **GIVEN** first joins through unknown hosts on a day
- **WHEN** the metric points are read
- **THEN** they are stored under the "direct" line and not under any channel

### Requirement: Call-to-action text with join host
(Should) The hub SHALL provide, per channel and per language, a call-to-action text that contains the channel's join host to the render bundle of `add-content-series`.

#### Scenario: Text in two languages
- **GIVEN** a channel with join host `yt.onelitefeather.net` and the languages `de` and `en`
- **WHEN** the call-to-action texts are read
- **THEN** each language has a text that contains the host

#### Scenario: Channel without join host
- **GIVEN** a channel with no join host
- **WHEN** the call-to-action texts are read
- **THEN** no text is provided for that channel and the bundle reports it as missing

#### Scenario: Text changed
- **GIVEN** a channel whose join host changes
- **WHEN** the call-to-action texts are read
- **THEN** the text carries the new host
