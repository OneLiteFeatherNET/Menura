# Design

## Context

Editors hold finished videos and images on their own machines. Distribution (`add-content-distribution`, phase 2) can only schedule assets that the hub stores, so this change puts finished assets into the private Menura bucket on the OLF Ceph object storage (principle 1), from the web app, without a desktop app (principle 2 is respected: no media work runs in the cluster). See proposal.md - Why for motivation.

Constraints from the project context: principle 1 (media in private Ceph buckets through the S3 API, presigned multipart uploads, the server never proxies bytes, metadata in PostgreSQL), principle 2 (the cluster runs no ffmpeg and no GPU work), principle 4 (nothing is published here), principle 6 (the browser holds only a session cookie, never a storage credential), principle 11 (secrets and links not logged, 404 for hidden objects, 429 with Retry-After), principle 15 (request bodies below 100 MB through the Cloudflare proxy, so parts are at most 95 MB).

Phase note: `add-content-library` (phase 4) later extends the asset records with lineage edges to timelines, clips and recordings. This change defines the record and the upload path those edges attach to; it does not model lineage, so the later change adds fields and edges without changing the upload contract.

## Goals / Non-Goals

**Goals:**
- A large finished video arrives intact after a dropped connection, without starting over.
- A stored asset is provably the file the editor declared, byte for byte.
- No media byte and no storage credential passes through the server or the browser's storage.

**Non-Goals:**
- No lineage (`add-content-library`), no transcoding, thumbnailing by ffmpeg or any probing of media on the server (principle 2).
- No recording upload and no session model for recordings (`add-recording-ingest`).
- No public links; no CDN delivery of assets (the Cloudflare terms check in principle 15 applies before any large video is served through the proxy).

## Decisions

**Presigned multipart upload from the browser.** The server creates the multipart upload in the bucket, issues one presigned URL per part with a short lifetime, and the browser sends each part straight to the storage endpoint. Parts are at most 95 MB, which keeps every request below the Cloudflare body limit of 100 MB; the storage endpoint is reached on its own public host (`s3.onelitefeather.net`, principle 14), so the request never crosses the API host.
- Rejected: a single PUT of the whole file. A 10 GB upload would fail on the first lost connection and would be sent through the request-body limit.
- Rejected: a server endpoint that receives parts and forwards them. It is forbidden by principle 1 and makes the server the bottleneck for all video traffic.

**Resume by confirmed parts, not by byte offsets.** The session records which parts the storage has confirmed (its part list). After an interruption the browser asks for the session and uploads only missing parts. A reload leads to the same session, because the session is found by its identifier, which the browser keeps in local storage as a convenience only.
- Rejected: restarting after an interruption. For multi-gigabyte files it wastes the bandwidth the editor already spent.

**Checksum declared up front, verified on completion.** The editor's client computes a SHA-256 checksum of the file before the upload starts and declares it with the session. On completion the server compares it with the stored object's checksum and lists the asset only when they match. A mismatch marks the session failed and keeps the bytes out of the library.
- Rejected: trusting the storage's completion response. It confirms the parts were joined, not that the content is the file the editor meant.

**Metadata declared by the uploader, never probed on the server.** Duration and resolution are read by the editor's client (for example from the file's media headers in the browser) and declared with the session. The server stores what was declared and checks what it can check itself, which is size and checksum. The server never runs ffmpeg or ffprobe (principle 2).
- Rejected: probing on the server. It would need a media toolchain in the cluster, which principle 2 forbids.
- Consequence: a wrong declared duration is possible. It is shown to the editor on the preview and can be corrected by re-uploading; it does not affect the checksum.

**Short-lived signed links for preview.** Listing returns metadata; preview and download use links signed for one object with an expiry of a few minutes, issued per request after the caller's role is checked. Hidden assets answer 404 before any link is made.
- Rejected: public or long-lived object URLs. They would survive a role change and could be shared outside the team.

**Deletion explicit, audited and refused while referenced.** Deleting an asset is a separate request by an editor. Before deleting, the system asks a reference check port whether any post refers to the asset; `add-content-distribution` implements that port. Each deletion writes one audit entry, using the audit mechanism of `add-access-control`.
- Rejected: lifecycle rules that expire unreferenced assets. Finished assets are curated; silent expiry would remove something a person still wants.

**Duplicate check flags, it does not block.** A checksum already recorded for the same uploader produces a flag with a reference to the existing asset. The editor decides, because the same file may be a different export or a re-upload after a fix.
- Rejected: refusing duplicates. Two valid exports can be byte-identical copies of a short that a publisher wants to keep in two places.

## Risks / Trade-offs

- [Risk] Browser memory use for large files → Mitigation: the client reads each part from the file handle in turn and never loads the whole file.
- [Risk] A presigned URL expires during a slow part → Mitigation: the client requests fresh URLs for the remaining parts when one is refused; the confirmed parts stay valid.
- [Risk] The storage endpoint is unavailable → Mitigation: the session stays uploading and resumes when the endpoint returns; the editor sees the state.
- [Risk] Declared metadata is wrong → Mitigation: shown on preview; size and checksum, which decide integrity, are verified.
- [Trade-off] Two hosts are involved in an upload (API for sessions, storage for parts), which means two CORS configurations. The storage allows the web app origin only, and the API allows the same origin; no other origin, wildcard or credentialed CORS is allowed (principle 14).

## Migration Plan

First release: no existing assets. Schema ships as forward-only migrations. Rollback deploys the previous server release; stored assets and their records remain, and no upload session is left half-applied without its record, because a session is only listed as an asset after verification.

## Open Questions

- Maximum finished-asset size per upload, which sets the number of parts and the session lifetime. Decided from the largest expected render before release; it does not change the specs.
- Whether assets are later served through the Cloudflare proxy for the website feed. Depends on the Cloudflare terms check (principle 15); not part of this change.
