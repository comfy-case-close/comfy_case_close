package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.config.GoogleCloudStorageProperties;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.service.AttachmentStorageService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Acl;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Google Cloud Storage-backed {@link AttachmentStorageService}.
 *
 * <p>Object key layout mirrors the legacy GAS build's Drive folder tree (see
 * {@code comfy_cash_close_package/Code.gs}: {@code getUploadFolder_()}) — GCS has no real
 * folders, so this is just a {@code /}-delimited key prefix, not an actual folder hierarchy:
 *
 * <pre>{@code <yyyy>/<MM>/<branchCode>/<fileName>}</pre>
 *
 * <p>Timezone for the year/month split is {@code Asia/Ho_Chi_Minh}, same as
 * {@code APP.timezone} in the legacy build, so files land in the same monthly bucket a human
 * would expect from the old system.
 *
 * <p><b>Visibility:</b> each uploaded object gets a best-effort per-object "allUsers: READER"
 * ACL so the frontend can render it directly as an {@code <img>} without a signed URL. If the
 * bucket has "uniform bucket-level access" enabled, per-object ACLs are rejected outright by
 * GCS — grant {@code allUsers} the "Storage Object Viewer" IAM role on the whole bucket instead
 * (one-time setup); this method logs a warning and continues either way, since the upload itself
 * still succeeded.
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
            Blob created = storage.create(blobInfo, file.getBytes());

            shareReadOnlyWithAnyone(created);

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
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }

    @Override
    public List<StoredObject> listObjectsOlderThan(Duration minAge) {
        requireEnabled();
        OffsetDateTime cutoff = OffsetDateTime.now(APP_ZONE).minus(minAge);
        List<StoredObject> found = new ArrayList<>();
        for (Blob blob : client().list(properties.getBucketName()).iterateAll()) {
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
        return year + "/" + month + "/" + branchSegment + "/" + fileName;
    }

    private void shareReadOnlyWithAnyone(Blob blob) {
        try {
            blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
        } catch (StorageException e) {
            // Non-fatal: most likely the bucket has uniform bucket-level access enabled, which
            // rejects per-object ACLs outright — see the class Javadoc for the bucket-level fix.
            log.warn("Could not set public-read ACL on GCS object {}: {}", blob.getName(), e.getMessage());
        }
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
        try (InputStream credentialsStream = new FileInputStream(keyPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        } catch (IOException e) {
            throw new BadRequestException(
                    "Could not read Google service account key at '" + keyPath + "': " + e.getMessage());
        }
    }
}
