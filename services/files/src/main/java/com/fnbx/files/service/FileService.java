package com.fnbx.files.service;

import com.fnbx.files.config.GcsStorageProperties;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.files.repository.StoredFileRepository;
import com.fnbx.files.storage.FileObjectStorage;
import com.fnbx.shared.enums.FileKind;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/** Uploads private objects and registers immutable metadata in the current DDL. */
@Service
@RequiredArgsConstructor
public class FileService {
    private final StoredFileRepository files;
    private final FileObjectStorage storage;
    private final GcsStorageProperties properties;
    private final BranchAccessGuard access;

    @Transactional
    public FileResponse upload(UUID branchId, MultipartFile file, FileKind kind) {
        access.require(branchId, Permission.CLOSE_EDIT);
        if (kind == null || kind == FileKind.AVATAR || file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A cash-close attachment file and kind are required");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Attachment exceeds the configured file size limit");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Attachment could not be read");
        }
        if (bytes.length == 0 || bytes.length > properties.getMaxFileSizeBytes()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Attachment size is invalid");
        }
        String name = safeName(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream" : file.getContentType();
        UUID businessId = TenantContext.current().businessId();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String prefix = properties.getObjectPrefix() == null ? "" :
                properties.getObjectPrefix().replaceAll("^/+|/+$", "");
        String key = (prefix.isEmpty() ? "" : prefix + "/") + businessId + "/"
                + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + branchId + "/" + UUID.randomUUID() + "-" + name;
        try {
            storage.put(key, bytes, contentType);
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Attachment storage is unavailable");
        }

        StoredFile saved = new StoredFile();
        saved.setFileId(UUID.randomUUID());
        saved.setBusinessId(businessId);
        saved.setBranchId(branchId);
        saved.setFileKind(kind);
        saved.setFileName(name);
        saved.setContentType(contentType);
        saved.setByteSize(bytes.length);
        saved.setSha256(sha256(bytes));
        saved.setStorageProvider("GCS");
        saved.setStorageKey(key);
        saved.setUploadedBy(TenantContext.current().userId());
        try {
            files.saveAndFlush(saved);
        } catch (RuntimeException e) {
            try { storage.delete(key); } catch (RuntimeException ignored) { /* best effort */ }
            throw e;
        }
        return new FileResponse(saved.getFileId(), saved.getFileKind(), saved.getFileName(),
                saved.getContentType(), saved.getByteSize());
    }

    @Transactional(readOnly = true)
    public ViewUrlResponse viewUrl(UUID branchId, UUID fileId) {
        access.require(branchId, Permission.CLOSE_READ);
        StoredFile file = files.findById(fileId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!TenantContext.current().businessId().equals(file.getBusinessId())
                || !branchId.equals(file.getBranchId())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return new ViewUrlResponse(file.getFileId(), storage.signedReadUrl(file.getStorageKey()),
                    properties.getSignedUrlTtlMinutes());
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Attachment link is unavailable");
        }
    }

    private static String safeName(String original) {
        String basename = original == null ? "attachment" : original.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return basename.isBlank() || basename.equals(".") || basename.equals("..")
                ? "attachment" : basename.substring(0, Math.min(basename.length(), 150));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record FileResponse(UUID fileId, FileKind kind, String fileName,
                               String contentType, long byteSize) {}
    public record ViewUrlResponse(UUID fileId, String url, long expiresInMinutes) {}
}
