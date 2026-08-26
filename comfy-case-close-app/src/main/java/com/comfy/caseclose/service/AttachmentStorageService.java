package com.comfy.caseclose.service;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Stores cash-close attachment files (photos) in object storage (Google Cloud Storage).
 *
 * <p>Mirrors the legacy GAS build's {@code uploadAttachments_()} / {@code getUploadFolder_()}
 * (see {@code comfy_cash_close_package/Code.gs}): files land grouped by year → month → branch,
 * and the returned URL is what gets persisted on {@code attachments.file_url}.
 *
 * <p>Originally implemented against Google Drive; switched to GCS because a service account has
 * no personal Drive storage quota outside Google Workspace, so uploads 403 regardless of folder
 * sharing. See {@link com.comfy.caseclose.config.GoogleCloudStorageProperties} for the reasoning
 * and setup steps.
 */
public interface AttachmentStorageService {

    /**
     * Uploads {@code file} under the {@code <year>/<month>/<branchCode>/} key prefix and returns
     * the stored object's metadata. Object storage has no real folders — this prefix is just
     * part of the object key, so unlike Drive there is no folder-creation step.
     *
     * @param file       the multipart image/file from the client
     * @param branchCode business key of the branch (e.g. {@code "TX"}), used as the leaf prefix
     *                   segment — same convention as the legacy {@code branchId} folder segment
     * @param namePrefix a short prefix baked into the stored file name for traceability (e.g.
     *                   the attachment type, such as {@code "POS_RECEIPT"})
     */
    UploadedFile uploadFile(MultipartFile file, String branchCode, String namePrefix);

    /**
     * Deletes a previously uploaded file by its object key. Used when an attachment row is
     * deleted, to avoid leaving orphaned files in storage.
     */
    void deleteFile(String objectKey);

    /**
     * Recovers the object key from a URL previously returned as {@link UploadedFile#fileUrl()}.
     * {@code Attachment} only persists the URL, not the raw key, so this is how
     * {@code AttachmentService#deleteAttachment} and the orphan-cleanup job (see
     * {@code OrphanAttachmentCleanupJob}) turn a stored URL back into something
     * {@link #deleteFile} accepts.
     *
     * @return the object key, or {@code null} if {@code fileUrl} wasn't produced by this service
     *         (e.g. a hand-typed URL from before the upload endpoint existed)
     */
    String objectKeyFromUrl(String fileUrl);

    /**
     * Lists every object currently in the bucket that was uploaded at least {@code minAge} ago —
     * i.e. old enough that, if nothing ever attached it to a submitted cash close, it's very
     * unlikely still to be a form someone is actively filling in. Used by the orphan-cleanup job
     * to find upload-but-never-submitted files (POST /attachments/upload writes the object before
     * any {@code Attachment} row exists — see {@code AttachmentController}'s Javadoc).
     */
    List<StoredObject> listObjectsOlderThan(Duration minAge);

    /**
     * Result of a successful upload.
     *
     * @param objectKey full GCS object key (needed to delete the file later)
     * @param fileUrl   canonical URL persisted on {@code attachments.file_url}, viewable in a
     *                  browser / renderable as an {@code <img>} on the frontend
     * @param fileName  the final stored file name (with the generated-unique suffix, no prefix)
     */
    record UploadedFile(String objectKey, String fileUrl, String fileName) {
    }

    /** One object found by {@link #listObjectsOlderThan}. */
    record StoredObject(String objectKey, OffsetDateTime createdAt) {
    }
}
