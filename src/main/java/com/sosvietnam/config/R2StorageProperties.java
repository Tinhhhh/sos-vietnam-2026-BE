package com.sosvietnam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cloudflare R2 settings (app.storage.r2.*). Secrets come from environment
 * variables or the git-ignored .env file, never from application.properties.
 */
@ConfigurationProperties(prefix = "app.storage.r2")
public record R2StorageProperties(
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        String bucket,
        String endpoint,
        Duration presignTtl
) {

    public R2StorageProperties {
        if (presignTtl == null) presignTtl = Duration.ofMinutes(10);
    }

    /** https://<account-id>.r2.cloudflarestorage.com unless an explicit endpoint is set. */
    public URI endpointUri() {
        if (endpoint != null && !endpoint.isBlank()) return URI.create(endpoint);
        return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
    }

    /** Names of the environment variables that are still empty. */
    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (isBlank(accountId) && isBlank(endpoint)) missing.add("R2_ACCOUNT_ID");
        if (isBlank(accessKeyId)) missing.add("R2_ACCESS_KEY_ID");
        if (isBlank(secretAccessKey)) missing.add("R2_SECRET_ACCESS_KEY");
        if (isBlank(bucket)) missing.add("R2_BUCKET");
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
