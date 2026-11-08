package net.onelitefeather.menura.server.library;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.asset.UploadLimits;

import java.time.Duration;

/**
 * Configuration of the library's storage and uploads, from the {@code menura.storage} and {@code menura.library}
 * sections of application.yml. Every value is an environment variable with a local-development default; the
 * credentials have no default and come from an existing Secret in the cluster (see the Helm chart).
 */
@Singleton
public class LibrarySettings {

    @Value("${menura.storage.endpoint}")
    String endpoint;

    @Value("${menura.storage.region}")
    String region;

    @Value("${menura.storage.bucket}")
    String bucket;

    @Value("${menura.storage.access-key-id}")
    String accessKeyId;

    @Value("${menura.storage.secret-access-key}")
    String secretAccessKey;

    @Value("${menura.library.part-size-bytes}")
    long partSizeBytes;

    @Value("${menura.library.raw-material-max-bytes}")
    long rawMaterialMaxBytes;

    @Value("${menura.library.upload-url-lifetime}")
    String uploadUrlLifetime;

    @Value("${menura.library.link-lifetime}")
    String linkLifetime;

    /** The S3 endpoint, which is also the host the browser sends parts to (principle 14). */
    public String endpoint() {
        return endpoint;
    }

    public String region() {
        return region;
    }

    public String bucket() {
        return bucket;
    }

    /** Empty when the default AWS credential chain should be used (local development). */
    public String accessKeyId() {
        return accessKeyId;
    }

    public String secretAccessKey() {
        return secretAccessKey;
    }

    public UploadLimits limits() {
        return new UploadLimits(partSizeBytes, rawMaterialMaxBytes);
    }

    /** How long a presigned part URL works. */
    public Duration uploadUrlLifetime() {
        return Duration.parse(uploadUrlLifetime);
    }

    /** How long a signed preview or download link works. */
    public Duration linkLifetime() {
        return Duration.parse(linkLifetime);
    }
}
