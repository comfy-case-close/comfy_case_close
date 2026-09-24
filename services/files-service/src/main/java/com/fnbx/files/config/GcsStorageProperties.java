package com.fnbx.files.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Private bucket configuration; the service account key is a mounted secret. */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Data
public class GcsStorageProperties {
    private boolean enabled;
    private String bucketName;
    private String serviceAccountKeyPath;
    private String objectPrefix = "case-close";
    private long signedUrlTtlMinutes = 60;
    private long maxFileSizeBytes = 10L * 1024 * 1024;
}
