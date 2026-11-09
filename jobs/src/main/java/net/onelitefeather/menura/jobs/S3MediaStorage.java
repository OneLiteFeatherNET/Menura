package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.publish.MediaStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Reads one byte range of a stored asset from the object storage (change add-youtube-integration). The publication job
 * streams a finished video to the platform chunk by chunk through this reader, so the server never handles media bytes and
 * the job never holds a whole file in memory. The range is requested with the HTTP Range header of the S3 API.
 *
 * <p>Variables (the same as the retention job): MENURA_S3_ENDPOINT, MENURA_S3_REGION, MENURA_S3_BUCKET,
 * MENURA_S3_ACCESS_KEY_ID and MENURA_S3_SECRET_ACCESS_KEY. Errors carry no signed URL and no SDK message.
 */
final class S3MediaStorage implements MediaStorage {

    private final S3Client s3;
    private final String bucket;

    S3MediaStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public byte[] read(String storageKey, long offset, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        String range = "bytes=" + offset + "-" + (offset + length - 1);
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(storageKey).range(range).build());
            return bytes.asByteArray();
        } catch (SdkException failure) {
            throw new IOException("The object storage did not return the requested range of the video.");
        }
    }

    /** The S3 client of the publication job, configured from the environment. */
    static S3Client client(Map<String, String> environment) {
        String access = environment.getOrDefault("MENURA_S3_ACCESS_KEY_ID", "");
        AwsCredentialsProvider credentials = access.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(access,
                        environment.getOrDefault("MENURA_S3_SECRET_ACCESS_KEY", "")));
        return S3Client.builder()
                .endpointOverride(URI.create(environment.getOrDefault("MENURA_S3_ENDPOINT", "http://localhost:3900")))
                .region(Region.of(environment.getOrDefault("MENURA_S3_REGION", "garage")))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** The bucket of the finished assets, from the environment. */
    static String bucket(Map<String, String> environment) {
        return environment.getOrDefault("MENURA_S3_BUCKET", "menura-dev");
    }
}
