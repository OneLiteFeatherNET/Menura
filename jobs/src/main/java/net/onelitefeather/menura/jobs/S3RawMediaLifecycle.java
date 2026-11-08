package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.library.retention.RawMediaLifecycle;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.StorageClass;

/**
 * Moves a recording to the cold storage class by copying the object onto itself with a new storage class. The object
 * keeps its key, so the raw item and its lineage keep pointing at the same storage key.
 */
final class S3RawMediaLifecycle implements RawMediaLifecycle {

    private final S3Client s3;
    private final String bucket;

    S3RawMediaLifecycle(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void moveToCold(String storageKey) {
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(storageKey)
                .destinationBucket(bucket)
                .destinationKey(storageKey)
                .storageClass(StorageClass.GLACIER_IR)
                .build());
    }
}
