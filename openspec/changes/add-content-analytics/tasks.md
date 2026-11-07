# Tasks

Requirements marked "Where ... is available" in the spec are built by the change that provides the component: recording, stream, contributor and tag links in `add-content-library` (phase 4), overlay state in `add-overlay-system` (phase 6), source post and revision comparisons in `add-content-distribution` (phase 2), MCP reads in `add-hub-mcp-server` (phase 3). The series and layout template dimensions read the catalogue of `add-content-series` and the brand kit of `add-brand-kit`, both phase 1 dependencies; the internal source tests use a test double, as the join-attribution source is built in `add-join-attribution`.

## 1. Metric model and collection

- [ ] 1.1 Write failing tests with an injected clock and a fake channel client that a scheduled run stores one point per external post, channel and metric, stores unavailable metrics as absent rather than zero, and records a channel failure while keeping earlier points; verify they fail for the expected reason
- [ ] 1.2 Implement the metric point model and the scheduled collection job using the metrics operation of the adapter port in `add-channel-connections`; verify the tests from 1.1 pass
- [ ] 1.3 Write failing tests that each point carries its external post, channel, platform, format and posting hour, and that an aggregate skips absent values and reports the count of points it used; verify they fail
- [ ] 1.4 Implement the dimension capture at write time and the aggregate count; verify the tests from 1.3 pass

## 2. Partitioned storage and rollups

- [ ] 2.1 Write a failing integration test (tagged `integration`) that the job creates the partition of a month before the first point of that month is collected, and that a point lands in its month partition; verify it fails
- [ ] 2.2 Implement the monthly range-partitioned metric table and the job step that creates the next month's partition ahead; verify the test from 2.1 passes
- [ ] 2.3 Write a failing integration test that daily and weekly rollups equal the sums of the raw points for each channel, format and posting hour combination, and that a corrected day rebuilds its rollups; verify it fails
- [ ] 2.4 Implement the rollup job for daily and weekly rollups and the rebuild on correction; verify the test from 2.3 passes
- [ ] 2.5 Write a failing integration test (tagged `integration`) that a dashboard aggregate by channel, format and posting hour over two years of generated points, read from rollups, returns within one second; verify it fails, then tune the rollup tables and indexes until it passes
- [ ] 2.6 Document the partitioning, rollup schedule and the decision with its rejected alternatives (ClickHouse, TimescaleDB, unpartitioned tables) in the operations notes of the jobs component; verify the notes list all three alternatives

## 3. Read access

- [ ] 3.1 Write failing tests that the REST aggregate endpoint returns rollup-based aggregates for viewer and higher roles, that the drill-down endpoint returns raw points, that an external post outside the caller's roles answers 404, and that no REST analytics endpoint can change an external post; verify they fail
- [ ] 3.2 Implement the REST read endpoints for aggregates, history, drill-down and the 404 rule; verify the tests from 3.1 pass
- [ ] 3.3 Write failing mount tests for the dashboard that the aggregate view renders the values returned by the REST endpoint; verify they fail
- [ ] 3.4 Implement the dashboard aggregate view; verify the tests from 3.3 pass

## 4. Export

- [ ] 4.1 (Could) Write a failing test that the Prometheus-compatible endpoint exposes aggregated external post metrics by channel and is disabled by default; verify it fails, then implement the endpoint and verify it passes, or skip this task and note the skip in the pull request

## 5. Dimensions and internal sources

- [ ] 5.1 Write failing tests that a metric point of an external post carries the series, layout template and brand kit version it was rendered with, that an imported post stores them as absent, and that a series reassignment changes only the points collected afterwards; verify they fail for the expected reason
- [ ] 5.2 Implement the capture of series, layout template and brand kit version at write time and their rollup dimensions; verify the tests from 5.1 pass
- [ ] 5.3 Write failing tests that a registered internal source writes points with its source and estimate flag, that an unregistered source or metric name is refused without storing anything, that an internal write leaves a platform point unchanged, and that a rollup marks the estimates it contains; verify they fail
- [ ] 5.4 Implement the internal source registration, the write path into the metric point table and the estimate flag in rollups; verify the tests from 5.3 pass

## 6. Series comparison

- [ ] 6.1 Write failing tests that the series comparison returns engaged views and average view percentage per series and week from the series-week rollup, shows the average view percentage as unavailable when no point provides it, shows the metrics of a registered internal source with its estimate flag, and ranks series by engaged views rather than views; verify they fail
- [ ] 6.2 Implement the series-week rollup, the comparison endpoint and the dashboard comparison view; verify the tests from 6.1 pass
- [ ] 6.3 Write a failing integration test (tagged `integration`) that the comparison of 52 weeks over 20 series and two years of generated points returns within one second from the rollup; verify it fails, then tune the rollup until it passes

## 7. Integration checks

- [ ] 7.1 Run the integration suite with the project's heavy-job lock (`flock ~/.cache/claude-heavy.lock`) and verify the partition, rollup, series comparison and two-year read tests pass

## 8. Delivery

- [ ] 8.1 Open the code pull request titled `feat(analytics): collect metrics of external posts`

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
