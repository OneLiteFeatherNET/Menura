# Tasks

## 1. Read adapter

- [ ] 1.1 Read the official TikTok video list and video object references; confirm the endpoint, the scopes, the field names and types and the page size; record the verified values and URLs in design.md; verify the rate-limit item is recorded as open or verified
- [ ] 1.2 Write failing unit tests with a fake TikTok read client and an injected clock: the listing returns public videos with identifier, URL, title, publish time and format; the listing pages through results; a private post absent from the listing is not imported and not marked removed; verify they fail for the expected reason
- [ ] 1.3 Implement the read-only TikTok client and the listing operation of the adapter; verify the tests from 1.2 pass

## 2. Metrics

- [ ] 2.1 Write failing unit tests with a fake client: views, likes, comments and shares are recorded for a public video; a count the API does not return is recorded as unavailable and not as zero; watch time and retention are recorded as unavailable; verify they fail
- [ ] 2.2 Implement the metrics operation of the adapter; verify the tests from 2.1 pass

## 3. Permissions

- [ ] 3.1 Write a failing test that the consent request for a channel connected for import contains the video list and basic account information scopes and no publish, upload or draft scope; verify it fails
- [ ] 3.2 Implement the read-only scope declaration of the adapter; verify the test from 3.1 passes

## 4. Channel view

- [ ] 4.1 Write a failing test that the channel view states that the import covers public videos only, and that a private post is not shown as removed; verify it fails
- [ ] 4.2 Implement the coverage note and the view state; verify the test from 4.1 passes

## 5. Documentation and integration check

- [ ] 5.1 Document in the operator guide the read-only scopes, the public-only coverage and the rate-limit handling; verify the steps are complete by following them on a test account
- [ ] 5.2 Run one read-only import against a test account with no write requests and verify the public videos and their counts appear as external posts
- [ ] 5.3 Open the code pull request titled `feat(tiktok): import existing public videos and read metrics`
