package net.onelitefeather.menura.server.library;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * An in-memory object storage that follows the rules the server relies on: a multipart upload with SHA-256 part
 * checksums gets a composite checksum ("base64-N") when joined, a presigned link is refused after its lifetime, and
 * an object can be removed. Parts are stored as the test sends them, so a test can send tampered bytes.
 */
@Singleton
@Replaces(S3ObjectStorage.class)
public class FakeObjectStorage implements ObjectStorage {

    private final Clock clock;
    private final Map<String, Multipart> uploads = new HashMap<>();
    private final Map<String, StoredObject> objects = new HashMap<>();
    private final Map<String, Instant> links = new HashMap<>();
    private int created;

    public FakeObjectStorage(Clock clock) {
        this.clock = clock;
    }

    /** Test-only constructor with an explicit clock, used by fixtures. */
    public static FakeObjectStorage withClock(Clock clock) {
        return new FakeObjectStorage(clock);
    }

    @Override
    public String createMultipartUpload(String key) {
        created++;
        String uploadId = "upload-" + created;
        uploads.put(uploadId, new Multipart(key));
        return uploadId;
    }

    @Override
    public SignedUrl presignUploadPart(String key, String uploadId, int partNumber, Duration lifetime) {
        String url = "https://s3.test/" + key + "?uploadId=" + uploadId + "&partNumber=" + partNumber + "&X-Amz-Signature=fake";
        Instant expiresAt = clock.instant().plus(lifetime);
        links.put(url, expiresAt);
        return new SignedUrl(url, expiresAt);
    }

    /** What a browser does with a presigned part URL: the storage keeps the part with the checksum the browser sent. */
    public void receivePart(String uploadId, int partNumber, long sizeBytes, String checksumSha256) {
        Multipart upload = uploadOf(uploadId);
        upload.parts.put(partNumber, new StoredPart(partNumber, sizeBytes, "etag-" + uploadId + "-" + partNumber, checksumSha256));
    }

    @Override
    public List<StoredPart> listParts(String key, String uploadId) {
        return new ArrayList<>(uploadOf(uploadId).parts.values());
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<StoredPart> parts) {
        Multipart upload = uploadOf(uploadId);
        long size = 0;
        List<byte[]> digests = new ArrayList<>();
        for (StoredPart part : parts) {
            StoredPart held = upload.parts.get(part.partNumber());
            if (held == null || !held.etag().equals(part.etag())) {
                throw new IllegalStateException("part " + part.partNumber() + " is not held by the storage");
            }
            size += held.sizeBytes();
            digests.add(Base64.getDecoder().decode(held.checksumSha256()));
        }
        objects.put(upload.key, new StoredObject(size, composite(digests)));
        uploads.remove(uploadId);
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        uploads.remove(uploadId);
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public SignedUrl presignGet(String key, String fileName, boolean attachment, Duration lifetime) {
        String url = "https://s3.test/" + key + "?response-content-disposition="
                + (attachment ? "attachment" : "inline") + "&X-Amz-Signature=fake";
        Instant expiresAt = clock.instant().plus(lifetime);
        links.put(url, expiresAt);
        return new SignedUrl(url, expiresAt);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    /** Whether the storage would still serve the link: it was issued by this storage and has not expired. */
    public boolean accepts(String url) {
        Instant expiresAt = links.get(url);
        return expiresAt != null && clock.instant().isBefore(expiresAt);
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    public int multipartUploadsCreated() {
        return created;
    }

    public int openUploads() {
        return uploads.size();
    }

    /** The composite checksum S3 reports for a multipart object: base64 of SHA-256 over the part digests, dash, count. */
    public static String composite(List<byte[]> partDigests) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            partDigests.forEach(digest::update);
            return Base64.getEncoder().encodeToString(digest.digest()) + "-" + partDigests.size();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Multipart uploadOf(String uploadId) {
        Multipart upload = uploads.get(uploadId);
        if (upload == null) {
            throw new ProblemException(503, "Storage Unavailable", "The object storage did not answer. Try again later.");
        }
        return upload;
    }

    private static final class Multipart {
        final String key;
        final TreeMap<Integer, StoredPart> parts = new TreeMap<>();

        Multipart(String key) {
            this.key = key;
        }
    }

    /** The SHA-256 of some bytes, base64 encoded as S3 reports part checksums. */
    public static String digestOf(byte[] bytes) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** UTF-8 helper for tests that build titles or bodies. */
    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
