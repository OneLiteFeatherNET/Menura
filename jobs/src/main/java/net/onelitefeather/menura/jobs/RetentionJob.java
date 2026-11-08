package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.library.retention.JdbcRawMediaCatalog;
import net.onelitefeather.menura.core.library.retention.RetentionRun;
import net.onelitefeather.menura.core.library.retention.RetentionSettings;
import org.postgresql.ds.PGSimpleDataSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

/**
 * Moves raw recordings older than the retention policy to the cold storage class and records the new media state.
 * Runs as a CronJob ({@code jobs run retention}). Disabled by default: with MENURA_RETENTION_ENABLED unset the run
 * ends at once and connects to neither the database nor the object storage.
 *
 * <p>Object storage variables: MENURA_S3_ENDPOINT, MENURA_S3_REGION, MENURA_S3_BUCKET, MENURA_S3_ACCESS_KEY_ID and
 * MENURA_S3_SECRET_ACCESS_KEY (the same keys as the server). Retention variables: see {@link RetentionSettings}.
 */
public final class RetentionJob implements Job {

    public static final String NAME = "retention";

    private final Map<String, String> environment;
    private final Clock clock;

    public RetentionJob(Map<String, String> environment, Clock clock) {
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute() throws Exception {
        RetentionSettings settings = RetentionSettings.from(environment);
        if (!settings.enabled()) {
            System.out.println("retention moved=0 policy=disabled");
            return;
        }
        MenuraSettings database = MenuraSettings.from(environment);
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(database.databaseUrl());
        dataSource.setUser(database.databaseUser());
        dataSource.setPassword(database.databasePassword());

        String bucket = environment.getOrDefault("MENURA_S3_BUCKET", "menura-dev");
        try (S3Client s3 = s3Client()) {
            int moved = new RetentionRun(settings, new JdbcRawMediaCatalog(new DataSourceTransactions(dataSource)),
                    new S3RawMediaLifecycle(s3, bucket)).run(clock.instant());
            System.out.println("retention moved=" + moved + " policy=enabled");
        }
    }

    private S3Client s3Client() {
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
}
