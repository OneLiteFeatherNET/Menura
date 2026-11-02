# API conventions

These rules apply to every endpoint of `menura.yaml`. The shared components named here (`ProblemDetails`,
`CursorPage`, `Identifier`, the `Problem` response and the `Limit` and `Cursor` parameters) exist in the contract,
and a contract check fails when they are missing. A new endpoint reuses them instead of inventing its own shape.

## Errors: Problem Details (RFC 9457)

- Every error response is an `application/problem+json` body with the members `type`, `title`, `status`, `detail`
  and `instance`.
- `type` is `about:blank` unless a specific problem type is documented. `instance` is the path of the request.
- `detail` explains the occurrence and never contains internal details: no stack traces, SQL, hostnames, secrets or
  the text of an unexpected exception.
- An object that the caller may not see answers 404, not 403, so that its existence is not revealed.

## Identifiers: version 4 UUIDs (RFC 9562)

- Every identifier is a random version 4 UUID in its canonical string form (`format: uuid`).
- Identifiers carry no creation time and no sequence, so they reveal neither the time nor the number of objects.
  Time-ordered variants such as version 7 are not used.

## Paging: cursor based

- A list takes `limit` (default 50, at most 200; a larger value is reduced to 200, a value below 1 is a 400
  Problem Details error) and an opaque `cursor`.
- A list answers with a `CursorPage` envelope: `items` and `nextCursor`. `nextCursor` is null on the last page.
- Clients treat the cursor as opaque. An invalid cursor is a 400 Problem Details error.
- Offset paging is not used: rows shift between pages and large tables pay for skipped rows.

## Timestamps: RFC 3339 in UTC

- Every timestamp is an RFC 3339 date-time in UTC with the `Z` suffix, for example `2026-11-02T19:30:00Z`.
- Timestamps are never local time and never carry a time-zone offset other than `Z`.

## Health

- `GET /health` is the only anonymous product endpoint of the skeleton besides the documentation. It reveals only
  the overall status and the database readiness (see `HealthStatus`).

## Contract changes

- `menura.yaml` is the source of truth. A change to an endpoint changes the contract first, in the same pull request
  as its tests, and the server serves the file unchanged.
- The contract lint and the shared-component checks run with the server tests (`./gradlew check`).
