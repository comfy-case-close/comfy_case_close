package com.fnbx.files.storage;

import com.fnbx.files.config.GcsStorageProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** The dev private-bucket upload and V4 signed-link behavior, owned by files-service. */
@Component
@RequiredArgsConstructor
public class GcsFileObjectStorage implements FileObjectStorage {
    private final GcsStorageProperties properties;
    private volatile Storage client;

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        client().create(BlobInfo.newBuilder(BlobId.of(properties.getBucketName(), key))
                .setContentType(contentType).build(), bytes);
    }

    @Override
    public String signedReadUrl(String key) {
        BlobInfo blob = BlobInfo.newBuilder(BlobId.of(properties.getBucketName(), key)).build();
        return client().signUrl(blob, properties.getSignedUrlTtlMinutes(), TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature()).toString();
    }

    @Override
    public void delete(String key) {
        client().delete(BlobId.of(properties.getBucketName(), key));
    }

    private Storage client() {
        if (!properties.isEnabled()) throw new IllegalStateException("GCS attachment storage is disabled");
        Storage result = client;
        if (result != null) return result;
        synchronized (this) {
            if (client != null) return client;
            if (properties.getBucketName() == null || properties.getBucketName().isBlank()
                    || properties.getServiceAccountKeyPath() == null
                    || properties.getServiceAccountKeyPath().isBlank()) {
                throw new IllegalStateException("GCS bucket and service account key path are required");
            }
            try (var key = Files.newInputStream(Path.of(properties.getServiceAccountKeyPath()))) {
                client = StorageOptions.newBuilder()
                        .setCredentials(GoogleCredentials.fromStream(key)).build().getService();
                return client;
            } catch (IOException e) {
                throw new IllegalStateException("GCS service account key could not be loaded", e);
            }
        }
    }
}
