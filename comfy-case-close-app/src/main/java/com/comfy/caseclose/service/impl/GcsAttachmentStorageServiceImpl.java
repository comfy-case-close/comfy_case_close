package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.config.GoogleCloudStorageProperties;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.service.AttachmentStorageService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage-backed {@link AttachmentStorageService}.
 *
 * <p>Object key layout mirrors the legacy GAS build's Drive folder tree (see
 * {@code comfy_cash_close_package/Code.gs}: {@code getUploadFolder_()}) — GCS has no real
 * folders, so this is just a {@code /}-delimited key prefix, not an actual folder hierarchy:
 *
 * <pre>{@code <objectPrefix>/<yyyy>/<MM>/<branchCode>/<fileName>}</pre>
 *
 * <p>{@code objectPrefix} is {@code case-close} by default: the bucket is shared, and this is the
 * folder attachments live in (and the only part of the bucket the orphan sweep may touch).
 *
 * <p>Timezone for the year/month split is {@code Asia/Ho_Chi_Minh}, same as
 * {@code APP.timezone} in the legacy build, so files land in the same monthly bucket a human
 * would expect from the old system.
 *
 * <p><b>Visibility:</b> the bucket is private (and has hierarchical namespace, which forces
 * uniform bucket-level access, so per-object ACLs are not an option anyway). Objects are never
 * made public; the frontend displays them through short-lived V4 signed URLs from
 * {@link #viewUrlFor}. Signing is done locally with the service account's private key — no
 * extra IAM role or network call needed.
 */
@Service
@RequiredArgsConstructor
public class GcsAttachmentStorageServiceImpl implements AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsAttachmentStorageServiceImpl.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final GoogleCloudStorageProperties properties;

    private volatile Storage storageClient;

    @PostConstruct
    void logConfigState() {
        if (!properties.isEnabled()) {
            log.warn("Google Cloud Storage attachment storage is DISABLED (app.storage.enabled=false). "
                    + "Attachment uploads will be rejected until credentials are configured.");
        }
    }

    @Override
    public UploadedFile uploadFile(MultipartFile file, String branchCode, String namePrefix) {
        requireEnabled();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required for attachment upload.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BadRequestException(
                    "File exceeds the maximum allowed size of " + properties.getMaxFileSizeBytes() + " bytes.");
        }

        Storage storage = client();
        String fileName = buildFileName(namePrefix, file);
        String objectKey = buildObjectKey(branchCode, fileName);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucketName(), objectKey))
                    .setContentType(contentType)
                    .build();
            storage.create(blobInfo, file.getBytes());

            return new UploadedFile(objectKey, publicUrlPrefix() + objectKey, fileName);
        } catch (IOException e) {
            log.error("Failed to read attachment file for upload", e);
            throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
        } catch (StorageException e) {
            log.error("Failed to upload attachment to Google Cloud Storage", e);
            throw new BadRequestException("Could not upload file to Google Cloud Storage: " + e.getMessage());
        }
    }

    @Override
    public String objectKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String prefix = publicUrlPrefix();
        if (!fileUrl.startsWith(prefix)) {
            // Not one of ours (e.g. a hand-typed URL) — nothing to delete from GCS.
            return null;
        }
        String key = fileUrl.substring(prefix.length());
        int queryStart = key.indexOf('?');
        if (queryStart >= 0) {
            // Tolerate a signed URL echoed back by a client — the key is everything before the query.
            key = key.substring(0, queryStart);
        }
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }

    /** Normalized key prefix with no leading slash and a trailing one, or empty for "bucket root". */
    private String keyPrefix() {
        String configured = properties.getObjectPrefix();
        if (configured == null) {
            return "";
        }
        String trimmed = configured.trim().replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? "" : trimmed + "/";
    }

    @Override
    public String viewUrlFor(String fileUrl) {
        if (!properties.isEnabled()) {
            return null;
        }
        String objectKey = objectKeyFromUrl(fileUrl);
        if (objectKey == null) {
            return null;
        }
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucketName(), objectKey)).build();
            return client()
                    .signUrl(blobInfo, properties.getSignedUrlTtlMinutes(), TimeUnit.MINUTES,
                            Storage.SignUrlOption.withV4Signature())
                    .toString();
        } catch (RuntimeException e) {
            // Includes BadRequestException from a missing/unreadable key and SigningException from
            // credentials that can't sign — a read must not fail just because the link can't be made.
            log.warn("Could not sign view URL for GCS object {}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    @Override
    public List<StoredObject> listObjectsOlderThan(Duration minAge) {
        requireEnabled();
        OffsetDateTime cutoff = OffsetDateTime.now(APP_ZONE).minus(minAge);
        List<StoredObject> found = new ArrayList<>();
        String prefix = keyPrefix();
        Iterable<Blob> blobs = prefix.isEmpty()
                ? client().list(properties.getBucketName()).iterateAll()
                : client().list(properties.getBucketName(), Storage.BlobListOption.prefix(prefix)).iterateAll();
        for (Blob blob : blobs) {
            if (blob.getName().endsWith("/")) {
                // Folder placeholder (hierarchical-namespace buckets), not an uploaded file.
                continue;
            }
            OffsetDateTime createdAt = blob.getCreateTimeOffsetDateTime();
            if (createdAt != null && createdAt.isBefore(cutoff)) {
                found.add(new StoredObject(blob.getName(), createdAt));
            }
        }
        return found;
    }

    private String publicUrlPrefix() {
        return "https://storage.googleapis.com/" + properties.getBucketName() + "/";
    }

    @Override
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        requireEnabled();
        try {
            client().delete(BlobId.of(properties.getBucketName(), objectKey));
        } catch (StorageException e) {
            // Best-effort: an attachment row can still be deleted from the DB even if the
            // underlying object is already gone or unreachable.
            log.warn("Could not delete GCS object {}: {}", objectKey, e.getMessage());
        }
    }

    // ----- object key (mirrors legacy getUploadFolder_'s year/month/branch split) -------------------

    private String buildObjectKey(String branchCode, String fileName) {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        String year = now.format(DateTimeFormatter.ofPattern("yyyy", Locale.ROOT));
        String month = now.format(DateTimeFormatter.ofPattern("MM", Locale.ROOT));
        String branchSegment = (branchCode == null || branchCode.isBlank()) ? "UNKNOWN" : branchCode.trim();
        return keyPrefix() + year + "/" + month + "/" + branchSegment + "/" + fileName;
    }

    private String buildFileName(String namePrefix, MultipartFile file) {
        String safePrefix = (namePrefix == null || namePrefix.isBlank())
                ? "ATTACHMENT"
                : namePrefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        String extension = extensionFor(file);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        return safePrefix + "_" + System.currentTimeMillis() + "_" + uniqueSuffix + extension;
    }

    private String extensionFor(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.'));
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return "";
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    // ----- client bootstrap -------------------------------------------------------------------------

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BadRequestException(
                    "Attachment upload is not configured yet. Set app.storage.enabled=true, "
                            + "app.storage.service-account-key-path and app.storage.bucket-name "
                            + "(see GoogleCloudStorageProperties for setup steps).");
        }
        if (properties.getBucketName() == null || properties.getBucketName().isBlank()) {
            throw new BadRequestException("app.storage.bucket-name is not configured.");
        }
    }

    private Storage client() {
        Storage existing = storageClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (storageClient == null) {
                storageClient = buildClient();
            }
            return storageClient;
        }
    }

    private Storage buildClient() {
        String keyPath = properties.getServiceAccountKeyPath();
        if (keyPath == null || keyPath.isBlank()) {
            throw new BadRequestException("app.storage.service-account-key-path is not configured.");
        }
        Path resolvedKeyPath = resolveKeyFile(keyPath);
        try (InputStream credentialsStream = new FileInputStream(resolvedKeyPath.toFile())) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        } catch (IOException e) {
            throw new BadRequestException(
                    "Could not read Google service account key at '" + resolvedKeyPath + "': " + e.getMessage());
        }
    }

    /**
     * Absolute paths are used as-is. A relative path is tried against the working directory and
     * then each parent directory, so one configured value works whether the app is started from the
     * repo root ({@code java -jar}) or from the module dir (IDE, {@code spring-boot:run}). If
     * nothing matches, the path is returned unchanged so the caller's error names what was configured.
     */
    private static Path resolveKeyFile(String keyPath) {
        Path configured = Path.of(keyPath);
        if (configured.isAbsolute()) {
            return configured;
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(configured);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return configured;
    }
}
