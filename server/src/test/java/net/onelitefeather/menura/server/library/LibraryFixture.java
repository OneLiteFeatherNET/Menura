package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.InMemoryAuditRepository;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.db.DirectTransactions;
import net.onelitefeather.menura.server.support.DeterministicRandom;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the real library services to in-memory collaborators, with a clock the test moves by hand. Each test builds
 * its own fixture, so no state is shared between tests.
 */
final class LibraryFixture {

    static final long MIB = 1024L * 1024;
    static final long GIB = 1024 * MIB;
    static final UUID EDITOR = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    static final UUID OTHER_EDITOR = UUID.fromString("1b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e");
    static final UUID VIEWER = UUID.fromString("2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f");

    final FixedClock clock = new FixedClock(Instant.parse("2026-11-08T10:00:00Z"));
    final DirectTransactions transactions = new DirectTransactions();
    final InMemoryLibraryRepositories.Sessions sessions = new InMemoryLibraryRepositories.Sessions();
    final InMemoryLibraryRepositories.Assets assets = new InMemoryLibraryRepositories.Assets();
    final InMemoryAuditRepository audit = new InMemoryAuditRepository();
    final FakeObjectStorage storage = FakeObjectStorage.withClock(clock);
    final FakeAssetReferences references = new FakeAssetReferences();
    final long partSize;
    final LibrarySettings settings;
    final IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom());
    final AuditLog auditLog = new AuditLog(transactions, clock, identifiers, audit);
    final AssetUploads uploads;
    final Assets library;

    /** A fixture with the production part size of 95 MB. */
    LibraryFixture() {
        this(95 * MIB);
    }

    /** A fixture whose parts have the given size, so a test can upload a few real bytes in several parts. */
    LibraryFixture(long partSize) {
        this.partSize = partSize;
        this.settings = settings(partSize);
        this.uploads = new AssetUploads(transactions, clock, identifiers, storage, sessions, assets, auditLog, settings);
        this.library = new Assets(transactions, storage, assets, references, auditLog, settings);
    }

    static LibrarySettings settings(long partSize) {
        LibrarySettings settings = new LibrarySettings();
        settings.endpoint = "https://s3.test";
        settings.region = "garage";
        settings.bucket = "menura-test";
        settings.accessKeyId = "";
        settings.secretAccessKey = "";
        settings.partSizeBytes = partSize;
        settings.rawMaterialMaxBytes = 50 * GIB;
        settings.uploadUrlLifetime = "PT15M";
        settings.linkLifetime = "PT5M";
        return settings;
    }

    static CurrentPerson editor() {
        return person(EDITOR, Set.of(Role.EDITOR));
    }

    static CurrentPerson otherEditor() {
        return person(OTHER_EDITOR, Set.of(Role.EDITOR));
    }

    static CurrentPerson viewer() {
        return person(VIEWER, Set.of(Role.VIEWER));
    }

    static CurrentPerson person(UUID id, Set<Role> roles) {
        return new CurrentPerson(id, "subject-" + id, roles, Channel.WEB);
    }

    /**
     * A well-formed checksum for a file of the given size, with the part count the storage will use. Its digest is
     * arbitrary, so it matches nothing: use it where the completion must refuse the file.
     */
    static String checksumFor(long sizeBytes, long partSize) {
        int parts = (int) ((sizeBytes + partSize - 1) / partSize);
        return "A".repeat(43) + "=-" + parts;
    }

    static AssetDeclaration video(long sizeBytes, String checksum) {
        return AssetDeclaration.declare("video", "mp4", 95, 1920, 1080, sizeBytes, checksum, null, null);
    }

    static AssetDeclaration rawRecording(long sizeBytes, String checksum) {
        return AssetDeclaration.declare("raw_material", "mkv", 7200, 1920, 1080, sizeBytes, checksum,
                "Coop night", "2026-11-01");
    }

    static AssetDeclaration image(long sizeBytes, String checksum) {
        return AssetDeclaration.declare("image", "png", null, 1280, 720, sizeBytes, checksum, null, null);
    }

    /** Uploads every part of a session as the browser would, with the digest of the bytes sent. */
    void sendAllParts(UUID sessionId, List<byte[]> partBytes) {
        String uploadId = sessions.find(null, sessionId).orElseThrow().storageUploadId();
        for (int part = 1; part <= partBytes.size(); part++) {
            byte[] bytes = partBytes.get(part - 1);
            storage.receivePart(uploadId, part, bytes.length, FakeObjectStorage.digestOf(bytes));
        }
    }

    /** Starts an upload, sends every part and completes it; returns the id of the asset it created. */
    UUID uploadAsset(CurrentPerson who, AssetDeclaration declaration, List<byte[]> partBytes) throws SQLException {
        UploadSessionView view = uploads.start(who, declaration, "req-start");
        sendAllParts(view.id(), partBytes);
        return uploads.complete(who, view.id(), "req-complete").assetId();
    }

    static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    /** The checksum the storage reports for these part bodies, as the editor's client declares it. */
    static String compositeOf(List<byte[]> partBytes) {
        return FakeObjectStorage.composite(partBytes.stream()
                .map(bytes -> Base64.getDecoder().decode(FakeObjectStorage.digestOf(bytes))).toList());
    }

    static final class FixedClock extends Clock {

        private Instant now;

        FixedClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class FakeAssetReferences implements AssetReferences {

        private UUID referencedBy;

        void referencedBy(UUID post) {
            this.referencedBy = post;
        }

        @Override
        public java.util.Optional<UUID> findReferencingPost(java.sql.Connection connection, UUID assetId) {
            return java.util.Optional.ofNullable(referencedBy);
        }
    }
}
