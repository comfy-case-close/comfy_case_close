package com.fnbx.files.entity;

import com.fnbx.shared.enums.FileKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored file. {@code files} owns the bytes; other modules keep a
 * {@code fileId} plus an immutable snapshot ({@link #publicUrl}, {@link #sha256}).
 *
 * <p>That snapshot is safe because a file never changes once uploaded, so no
 * module ever has to re-sync. The day file-service is extracted, dropping the
 * constraint is the whole migration - no consumer, no re-sync job, no monitoring.
 *
 * <p>Contrast with branch names, which do change, so other modules must join
 * rather than copy.
 */
@Entity
@Table(schema = "files", name = "stored_file")
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

    @Id
    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, columnDefinition = "shared.file_kind")
    private FileKind fileKind;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /** Enables dedupe: the same photo uploaded twice stores one byte stream. */
    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "storage_provider", nullable = false)
    private String storageProvider = "S3";

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "public_url")
    private String publicUrl;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private Instant uploadedAt;

    /** OCR: files triggers it, ai-service processes, the result lands back here. */
    @Column(name = "ocr_status", nullable = false)
    private String ocrStatus = "NONE";

    @Column(name = "ocr_text")
    private String ocrText;

    @Column(name = "ocr_extracted", columnDefinition = "jsonb")
    private String ocrExtracted;
}
