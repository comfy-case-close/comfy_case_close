package com.comfy.caseclose.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores cash-close attachment files (photos) in Google Drive.
 *
 * <p>Mirrors the legacy GAS build's {@code uploadAttachments_()} / {@code getUploadFolder_()}
 * (see {@code comfy_cash_close_package/Code.gs}): files land under a root folder, grouped by
 * year → month → branch, and the returned URL is what gets persisted on
 * {@code attachments.file_url}.
 */
public interface DriveStorageService {

    /**
     * Uploads {@code file} into the {@code <root>/<year>/<month>/<branchCode>} folder (creating
     * any missing folder in that path) and returns the stored file's metadata.
     *
     * @param file       the multipart image/file from the client
     * @param branchCode business key of the branch (e.g. {@code "TX"}), used as the leaf folder
     *                   name — same convention as the legacy {@code branchId} folder segment
     * @param namePrefix a short prefix baked into the stored file name for traceability (e.g.
     *                   the attachment type, such as {@code "POS_RECEIPT"})
     */
    UploadedFile uploadFile(MultipartFile file, String branchCode, String namePrefix);

    /**
     * Deletes a previously uploaded file by its Drive file ID. Used when an attachment row is
     * deleted, to avoid leaving orphaned files in Drive.
     */
    void deleteFile(String fileId);

    /**
     * Result of a successful upload.
     *
     * @param fileId   Drive file ID (needed to delete the file later)
     * @param fileUrl  canonical URL persisted on {@code attachments.file_url}, viewable in a
     *                 browser / renderable as an {@code <img>} on the frontend
     * @param fileName the final stored file name (with the generated-unique suffix)
     */
    record UploadedFile(String fileId, String fileUrl, String fileName) {
    }
}
