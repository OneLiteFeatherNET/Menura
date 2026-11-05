# Tasks

## 1. Read adapter

- [ ] 1.1 Read the official YouTube Data API and YouTube Analytics references for the channel-uploads listing, its read scope, the per-video statistics fields and the Analytics metric names and scope; record the verified values in design.md Platform constraints with their URLs; verify each open item is either recorded or marked as still open
- [ ] 1.2 Write failing unit tests with a fake YouTube read client and an injected clock: the listing returns public and unlisted videos with platform, video identifier, URL, title and publish time; private videos are excluded; no write call is made; verify they fail for the expected reason
- [ ] 1.3 Implement the read-only YouTube client and the listing operation of the adapter; verify the tests from 1.2 pass

## 2. Format

- [ ] 2.1 Write failing unit tests that a Short is recorded with format Short and a long video with format video, using the classification rule verified in 1.1; verify they fail
- [ ] 2.2 Implement the format classification; verify the tests from 2.1 pass

## 3. Statistics

- [ ] 3.1 Write failing unit tests with a fake client: views, watch time, likes and comments are returned for a video; a metric the channel does not return is recorded as unavailable and not as zero; each read call is counted against the connection's quota state; verify they fail
- [ ] 3.2 Implement the metrics operation of the adapter using the Data API statistics and the Analytics API; verify the tests from 3.1 pass

## 4. Permissions

- [ ] 4.1 Write a failing test that the consent request for a channel connected for statistics only contains the read scope and no upload, live stream or comment scope; verify it fails
- [ ] 4.2 Implement the scope declaration of the read feature in the adapter; verify the test from 4.1 passes

## 5. Documentation and integration check

- [ ] 5.1 Document in the operator guide the read-only scope, the quota cost of reads and the public and unlisted import rule; verify the steps are complete by following them on a test project
- [ ] 5.2 Run one read-only import against a test channel with no write requests and verify the videos, Shorts and statistics appear as external posts
- [ ] 5.3 Open the code pull request titled `feat(youtube): import existing videos and read statistics`
