package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The presigned multipart round trip against PostgreSQL 17 and a MinIO S3 API. The browser side is played with the Java
 * HTTP client sending each part to its presigned URL. Covers resume after a dropped part, the checksum match, and the
 * tampered upload that ends as failed and lists nothing.
 */
@Tag("integration")
@Testcontainers
class AssetUploadRoundTripIntegrationTest {

    private static final long PART = 5L * 1024 * 1024;
    private static final CurrentPerson EDITOR = new CurrentPerson(
            UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"), "subject", Set.of(Role.EDITOR), Channel.WEB);
    private static final String BUCKET = "menura-it";
    private static final UUID OTHER_EDITOR_ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
    private static final UUID VIEWER_ID = UUID.fromString("6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c");
    private static final long MIB = 1024L * 1024;

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Container
    MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-11-07T00-52-20Z"));

    private final HttpClient browser = HttpClient.newHttpClient();

    @Test
    void droppedPartIsResumedAndTheVerifiedUploadEndsAsAvailableAsset() throws Exception {
        Fixture f = new Fixture();
        List<byte[]> parts = parts(11 * 1024 * 1024, 42);
        AssetDeclaration declaration = video(sum(parts), LibraryFixture.compositeOf(parts));
        UploadSessionView started = f.uploads.start(EDITOR, declaration, "req-start");

        put(started.parts().get(0), parts.get(0));
        UploadSessionView afterDrop = f.uploads.read(EDITOR, started.id());
        assertEquals(List.of(1), afterDrop.confirmedParts(), "the storage holds only the part that arrived");
        assertEquals(2, afterDrop.nextPart(), "the upload continues with the first part not confirmed");

        for (PartUrl part : afterDrop.parts()) {
            put(part, parts.get(part.partNumber() - 1));
        }
        UploadSessionView done = f.uploads.complete(EDITOR, started.id(), "req-complete");

        assertEquals("available", done.state(), "the matching checksum makes the upload available");
        assertEquals(1, f.library.list(EDITOR, PageRequest.of(null, null)).items().size(), "the asset is listed");
        assertEquals(declaration.checksum().value(), f.storage.head(UploadSession.keyFor(started.id())).orElseThrow()
                .checksumSha256(), "the stored object carries the declared checksum");
    }

    @Test
    void tamperedPartEndsAsFailedAndNothingIsListed() throws Exception {
        Fixture f = new Fixture();
        List<byte[]> declared = parts(11 * 1024 * 1024, 7);
        List<byte[]> sent = new ArrayList<>(declared);
        sent.set(1, parts((int) PART, 99).get(0));
        UploadSessionView started = f.uploads.start(EDITOR,
                video(sum(declared), LibraryFixture.compositeOf(declared)), "req-start");
        for (PartUrl part : started.parts()) {
            put(part, sent.get(part.partNumber() - 1));
        }

        ProblemException refused = assertThrows(ProblemException.class,
                () -> f.uploads.complete(EDITOR, started.id(), "req-complete"));

        assertEquals(422, refused.status(), "the editor sees the mismatch");
        assertEquals("failed", f.uploads.read(EDITOR, started.id()).state(), "the session is marked failed");
        assertTrue(f.storage.head(UploadSession.keyFor(started.id())).isEmpty(), "the bytes of the wrong file are removed");
        assertEquals(0, f.library.list(EDITOR, PageRequest.of(null, null)).items().size(), "nothing is listed");
    }

    /**
     * The SQL listing shows the verified assets of the shared pool to every reader, raw material of other uploaders
     * included, and never a pending or a failed upload.
     */
    @Test
    void listingShowsVerifiedFinishedAndRawAssetsButNeverPendingOrFailedUploads() throws Exception {
        Fixture f = new Fixture();
        CurrentPerson otherEditor = new CurrentPerson(OTHER_EDITOR_ID, "other", Set.of(Role.EDITOR), Channel.WEB);
        CurrentPerson viewer = new CurrentPerson(VIEWER_ID, "viewer", Set.of(Role.VIEWER), Channel.WEB);

        List<byte[]> finishedParts = parts((int) MIB, 11);
        UUID finished = complete(f, EDITOR, video(MIB, LibraryFixture.compositeOf(finishedParts)), finishedParts);

        List<byte[]> rawParts = parts((int) MIB, 12);
        AssetDeclaration raw = AssetDeclaration.declare("raw_material", "mkv", 7200, 1920, 1080, MIB,
                LibraryFixture.compositeOf(rawParts), "Coop night", "2026-11-01");
        UUID rawAsset = complete(f, EDITOR, raw, rawParts);

        List<byte[]> pendingParts = parts((int) MIB, 13);
        UploadSessionView pending = f.uploads.start(otherEditor,
                video(MIB, LibraryFixture.compositeOf(pendingParts)), "req-pending");
        put(pending.parts().get(0), pendingParts.get(0));

        List<byte[]> declaredParts = parts((int) MIB, 14);
        List<byte[]> sentParts = parts((int) MIB, 15);
        UploadSessionView failing = f.uploads.start(otherEditor,
                video(MIB, LibraryFixture.compositeOf(declaredParts)), "req-failing");
        put(failing.parts().get(0), sentParts.get(0));
        assertThrows(ProblemException.class, () -> f.uploads.complete(otherEditor, failing.id(), "req-failing-complete"));

        List<UUID> listed = f.library.list(viewer, PageRequest.of(null, null)).items().stream()
                .map(AssetView::id).toList();

        assertEquals(List.of(rawAsset, finished), listed,
                "the viewer sees both verified assets, newest first, and nothing else");
        assertFalse(listed.contains(pending.id()), "a pending upload is not listed");
        assertFalse(listed.contains(failing.id()), "a failed upload is not listed");
        assertEquals(rawAsset, f.library.get(viewer, rawAsset).id(), "the raw item of another uploader is readable");
    }

    private UUID complete(Fixture f, CurrentPerson who, AssetDeclaration declaration, List<byte[]> bytes) throws Exception {
        UploadSessionView started = f.uploads.start(who, declaration, "req-start");
        for (PartUrl part : started.parts()) {
            put(part, bytes.get(part.partNumber() - 1));
        }
        return f.uploads.complete(who, started.id(), "req-complete").assetId();
    }

    /** One set of services over the real database and storage, for one test. */
    private final class Fixture {

        final Clock clock = Clock.systemUTC();
        final ObjectStorage storage;
        final AssetUploads uploads;
        final Assets library;

        Fixture() throws Exception {
            Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            LibrarySettings settings = LibraryFixture.settings(PART);
            settings.endpoint = minio.getS3URL();
            settings.bucket = BUCKET;
            settings.accessKeyId = minio.getUserName();
            settings.secretAccessKey = minio.getPassword();
            settings.uploadUrlLifetime = "PT10M";
            settings.linkLifetime = "PT5M";
            S3Client s3 = S3Client.builder()
                    .endpointOverride(URI.create(settings.endpoint))
                    .region(Region.of(settings.region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            settings.accessKeyId, settings.secretAccessKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
            S3Presigner presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(settings.endpoint))
                    .region(Region.of(settings.region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            settings.accessKeyId, settings.secretAccessKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
            createBucketOnce(s3);
            storage = new S3ObjectStorage(s3, presigner, settings, clock);
            DataSourceTransactions transactions = new DataSourceTransactions(dataSource());
            IdentifierGenerator identifiers = new IdentifierGenerator(new SecureRandom());
            AuditLog auditLog = new AuditLog(transactions, clock, identifiers, new JdbcAuditRepository());
            JdbcUploadSessionRepository sessions = new JdbcUploadSessionRepository();
            JdbcAssetRepository assets = new JdbcAssetRepository();
            uploads = new AssetUploads(transactions, clock, identifiers, storage, sessions, assets, auditLog, settings);
            library = new Assets(transactions, storage, assets, new AssetReferences.NoAssetReferences(), auditLog, settings);
        }

        private void createBucketOnce(S3Client s3) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            } catch (RuntimeException alreadyExists) {
                // The bucket of an earlier test in this run exists; the test uses its own object keys.
            }
        }

        private PGSimpleDataSource dataSource() {
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            return source;
        }
    }

    /** The browser's part request: the bytes go straight to the presigned URL with the part's SHA-256. */
    private void put(PartUrl part, byte[] bytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(part.url()))
                .header("x-amz-checksum-sha256", FakeObjectStorage.digestOf(bytes))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> response = browser.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the storage accepts the part: " + response.body());
    }

    private static List<byte[]> parts(int totalBytes, long seed) {
        Random random = new Random(seed);
        List<byte[]> parts = new ArrayList<>();
        int remaining = totalBytes;
        while (remaining > 0) {
            int size = (int) Math.min(PART, remaining);
            byte[] bytes = new byte[size];
            random.nextBytes(bytes);
            parts.add(bytes);
            remaining -= size;
        }
        return parts;
    }

    private static long sum(List<byte[]> parts) {
        return parts.stream().mapToLong(part -> part.length).sum();
    }

    private static AssetDeclaration video(long sizeBytes, String checksum) {
        return AssetDeclaration.declare("video", "mp4", 95, 1920, 1080, sizeBytes, checksum, null, null);
    }
}
