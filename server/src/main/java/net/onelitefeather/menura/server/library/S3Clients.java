package net.onelitefeather.menura.server.library;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the S3 client and presigner from {@link LibrarySettings}. Path-style addressing is used for every endpoint,
 * because the Ceph RGW and the local development store serve one host for all buckets. Without a configured key pair
 * the default AWS credential chain applies; nothing is built with a literal credential.
 */
@Factory
public class S3Clients {

    @Singleton
    S3Client s3Client(LibrarySettings settings) {
        return S3Client.builder()
                .endpointOverride(URI.create(settings.endpoint()))
                .region(Region.of(settings.region()))
                .credentialsProvider(credentials(settings))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Singleton
    S3Presigner s3Presigner(LibrarySettings settings) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(settings.endpoint()))
                .region(Region.of(settings.region()))
                .credentialsProvider(credentials(settings))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    static AwsCredentialsProvider credentials(LibrarySettings settings) {
        if (settings.accessKeyId() == null || settings.accessKeyId().isBlank()) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKeyId(),
                settings.secretAccessKey()));
    }
}
