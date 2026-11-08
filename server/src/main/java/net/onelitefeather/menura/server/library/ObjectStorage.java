package net.onelitefeather.menura.server.library;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The storage port of the library: the S3 API of the private Menura bucket, seen as the operations an upload and a
 * signed link need. The server calls it for control operations only; media bytes never pass through it (principle 1).
 * Production uses {@link S3ObjectStorage}; unit tests use a fake that models the same rules.
 */
public interface ObjectStorage {

    /** Creates a multipart upload with SHA-256 part checksums and returns its upload id. */
    String createMultipartUpload(String key);

    /** A presigned PUT for one part, valid for the lifetime. */
    SignedUrl presignUploadPart(String key, String uploadId, int partNumber, Duration lifetime);

    /** The parts the storage has received, with their ETags and part checksums. */
    List<StoredPart> listParts(String key, String uploadId);

    /** Joins the parts into the object; the storage then holds the composite checksum of the object. */
    void completeMultipartUpload(String key, String uploadId, List<StoredPart> parts);

    void abortMultipartUpload(String key, String uploadId);

    /** The stored object's size and checksum, or empty when no object exists under the key. */
    Optional<StoredObject> head(String key);

    /** A presigned GET for one object, valid for the lifetime; the content disposition names the file. */
    SignedUrl presignGet(String key, String fileName, boolean attachment, Duration lifetime);

    void delete(String key);

    /** A link issued by the storage and the instant after which the storage refuses it. */
    record SignedUrl(String url, Instant expiresAt) {
    }

    record StoredPart(int partNumber, long sizeBytes, String etag, String checksumSha256) {
    }

    record StoredObject(long sizeBytes, String checksumSha256) {
    }
}
