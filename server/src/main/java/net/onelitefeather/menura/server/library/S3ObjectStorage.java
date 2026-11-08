package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The S3 implementation of {@link ObjectStorage} for the OLF Ceph object storage (or any S3 API, such as MinIO in
 * tests). Storage failures become a 503 Problem Details response that never carries the SDK's message, because that
 * message may contain a signed URL.
 */
@Singleton
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger LOG = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    public S3ObjectStorage(S3Client s3, S3Presigner presigner, LibrarySettings settings, Clock clock) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = settings.bucket();
        this.clock = clock;
    }

    @Override
    public String createMultipartUpload(String key) {
        return call(() -> s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(key).checksumAlgorithm(ChecksumAlgorithm.SHA256).build()).uploadId());
    }

    @Override
    public SignedUrl presignUploadPart(String key, String uploadId, int partNumber, Duration lifetime) {
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber).build();
        PresignedUploadPartRequest presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(lifetime).uploadPartRequest(request).build());
        return new SignedUrl(presigned.url().toString(), clock.instant().plus(lifetime));
    }

    @Override
    public List<StoredPart> listParts(String key, String uploadId) {
        List<StoredPart> parts = new ArrayList<>();
        Integer marker = null;
        do {
            ListPartsRequest.Builder request = ListPartsRequest.builder().bucket(bucket).key(key).uploadId(uploadId);
            if (marker != null) {
                request.partNumberMarker(marker);
            }
            ListPartsResponse page = call(() -> s3.listParts(request.build()));
            for (Part part : page.parts()) {
                parts.add(new StoredPart(part.partNumber(), part.size(), part.eTag(), part.checksumSHA256()));
            }
            marker = page.isTruncated() ? page.nextPartNumberMarker() : null;
        } while (marker != null);
        return parts;
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<StoredPart> parts) {
        List<CompletedPart> completed = parts.stream()
                .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.etag())
                        .checksumSHA256(part.checksumSha256()).build())
                .toList();
        call(() -> s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build()));
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        call(() -> s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId)));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            var head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key)
                    .checksumMode(ChecksumMode.ENABLED).build());
            return Optional.of(new StoredObject(head.contentLength(), head.checksumSHA256()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (SdkException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public SignedUrl presignGet(String key, String fileName, boolean attachment, Duration lifetime) {
        String disposition = (attachment ? "attachment" : "inline") + "; filename=\"" + fileName + "\"";
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentDisposition(disposition).build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(lifetime).getObjectRequest(request).build());
        return new SignedUrl(presigned.url().toString(), clock.instant().plus(lifetime));
    }

    @Override
    public void delete(String key) {
        call(() -> s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()));
    }

    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SdkException failure) {
            throw unavailable(failure);
        }
    }

    private static ProblemException unavailable(SdkException failure) {
        // The class, HTTP status and S3 error code only: the message can carry a signed URL or a request detail.
        String code = failure instanceof AwsServiceException service && service.awsErrorDetails() != null
                ? service.awsErrorDetails().errorCode() : "none";
        LOG.warn("The object storage call failed with {} (S3 error code {})", failure.getClass().getSimpleName(), code);
        return new ProblemException(503, "Storage Unavailable",
                "The object storage did not answer. Try again later.");
    }
}
