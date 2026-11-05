# Tasks

## 1. Read adapter

- [ ] 1.1 Read the Instagram Graph API references for the account's media listing and its paging, the Reel insights and the account type requirement for insights; record the verified values and URLs in design.md; verify the open items are recorded as verified or still open
- [ ] 1.2 Write failing unit tests with a fake Instagram read client and an injected clock: the listing returns Reels with identifier, URL, caption as title, publish time and format Reel; a feed post in the listing is not imported; the listing pages through results; verify they fail for the expected reason
- [ ] 1.3 Implement the read-only Instagram client and the listing operation of the adapter; verify the tests from 1.2 pass

## 2. Insights

- [ ] 2.1 Write failing unit tests with a fake client: the reported insights of a Reel are stored as metric points with their names; an insight the response omits is recorded as unavailable and not as zero; a Reel published less than 48 hours ago has unreported figures that are collected again on a later run; verify they fail
- [ ] 2.2 Implement the insights metrics operation of the adapter and the re-collection window; verify the tests from 2.1 pass

## 3. Permissions

- [ ] 3.1 Write a failing test that the consent request for an account connected for import and insights contains `instagram_business_basic` and `instagram_business_manage_insights` and no publishing permission; verify it fails
- [ ] 3.2 Implement the read-only permission declaration of the adapter; verify the test from 3.1 passes

## 4. Documentation and integration check

- [ ] 4.1 Document in the operator guide the read-only permissions, the 48-hour insight delay and the Reels-only import; verify the steps are complete by following them on a test account
- [ ] 4.2 Run one read-only import against a test account with no write requests and verify its Reels and their insights appear as external posts
- [ ] 4.3 Open the code pull request titled `feat(instagram): import existing Reels and read insights`
