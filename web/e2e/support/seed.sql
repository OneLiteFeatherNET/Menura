-- Test-only seed of the e2e environment (web/e2e/support/run-server.mjs runs it after Flyway has migrated). It creates one
-- connected channel and one registered finished asset, so the calendar flow can plan a post. Every identifier and name is
-- fictitious: the channel has no platform grant (no token is stored, so no ciphertext is needed), and the asset has no
-- object in storage. The statements are idempotent, so a retry after a partial start changes nothing.

INSERT INTO menura.channel (id, platform, platform_account_id, display_name, state, features, granted_scopes,
                            created_at, updated_at)
VALUES ('c4e2e000-0000-4000-8000-000000000001', 'youtube', 'e2e-seed-channel-not-real', 'E2E-Kanal', 'connected',
        '{}', '{}', TIMESTAMPTZ '2026-11-01T10:00:00Z', TIMESTAMPTZ '2026-11-01T10:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO menura.asset (id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, checksum,
                          storage_key, created_at)
VALUES ('e2e5e100-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000e2', 'video', 'mp4', 120, 1080, 1920,
        52428800, 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=-1', 'e2e-seed/finished-asset.mp4',
        TIMESTAMPTZ '2026-11-01T10:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO menura.timeline (id, uploader_id, title, document, created_at)
VALUES ('e2e5e100-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000e2', 'E2E-Clip Pool',
        '{"layoutTemplate": "short-9x16", "sourceClipIds": [], "overlays": [], "captions": [], "chapters": []}',
        TIMESTAMPTZ '2026-11-01T10:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO menura.registered_asset (asset_id, uploader_id, title, timeline_id, registered_at)
VALUES ('e2e5e100-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000e2', 'E2E-Clip Pool',
        'e2e5e100-0000-4000-8000-000000000002', TIMESTAMPTZ '2026-11-01T10:00:00Z')
ON CONFLICT (asset_id) DO NOTHING;
