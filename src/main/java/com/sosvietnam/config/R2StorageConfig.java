package com.sosvietnam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 SDK clients pointed at Cloudflare R2. Active only when
 * app.storage.provider=r2. Settings follow Cloudflare's Java guide:
 * region "auto", path-style URLs, no chunked encoding (otherwise PUT fails
 * with a signature mismatch). Checksums are only sent when an operation
 * requires them: SDK 2.30+ adds CRC32 headers by default.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
@EnableConfigurationProperties(R2StorageProperties.class)
public class R2StorageConfig {

    private static final Region R2_REGION = Region.of("auto");

    @Bean(destroyMethod = "close")
    public S3Client r2Client(R2StorageProperties props) {
        requireConfigured(props);
        return S3Client.builder()
                .endpointOverride(props.endpointUri())
                .region(R2_REGION)
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2Presigner(R2StorageProperties props) {
        requireConfigured(props);
        return S3Presigner.builder()
                .endpointOverride(props.endpointUri())
                .region(R2_REGION)
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static StaticCredentialsProvider credentials(R2StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey()));
    }

    private static void requireConfigured(R2StorageProperties props) {
        var missing = props.missingSettings();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("app.storage.provider=r2 but these settings are empty: "
                    + String.join(", ", missing) + ". Put them in the .env file (see .env.example).");
        }
    }
}
