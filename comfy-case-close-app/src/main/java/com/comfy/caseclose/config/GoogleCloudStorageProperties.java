package com.comfy.caseclose.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Storage config for cash-close attachments (photos of the POS slip, cash
 * drawer, withdrawal proof, unpaid-bill repayment proof, etc.).
 *
 * <p>Originally built against Google Drive (mirroring the legacy GAS build's file-storage
 * behaviour — see {@code comfy_cash_close_package/Code.gs}: {@code APP.uploadRootFolderName},
 * {@code getUploadFolder_()}), but a service account has no personal Drive storage quota on a
 * non-Workspace account: every upload 403s with {@code storageQuotaExceeded} no matter how the
 * folder is shared (Google's own error points at exactly two fixes, both Workspace-only —
 * Shared Drives or domain-wide delegation). A GCS bucket has no such per-account quota, so the
 * same service-account credential works unchanged; only the destination moved.
 *
 * <p>Setup (do this once per environment):
 * <ol>
 *   <li>Google Cloud Console → enable the Cloud Storage API for the project.</li>
 *   <li>Create a Service Account, download its JSON key. Point
 *       {@code app.storage.service-account-key-path} at that file (or set
 *       {@code GCS_SERVICE_ACCOUNT_KEY} env var). Never commit the key file.</li>
 *   <li>Create (or pick) a bucket, grant the service account's {@code client_email} the
 *       "Storage Object Admin" role on it, then set {@code app.storage.bucket-name} to the
 *       bucket's name.</li>
 * </ol>
 */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Data
public class GoogleCloudStorageProperties {

    /**
     * Whether GCS upload is enabled. When {@code false}, {@code AttachmentService} rejects
     * upload requests with a clear message instead of failing to build a storage client — lets
     * the rest of the app run before GCS credentials are provisioned.
     */
    private boolean enabled = false;

    /**
     * Absolute path to the Service Account JSON key file. Keep this outside the repo (e.g. a
     * mounted secret, or a path outside the working tree) and point env var
     * {@code GCS_SERVICE_ACCOUNT_KEY} at it in every real environment.
     */
    private String serviceAccountKeyPath;

    /**
     * Name of the GCS bucket attachments are uploaded into (must already grant the service
     * account's {@code client_email} the Storage Object Admin role — a service account cannot
     * grant itself access to a bucket it doesn't own).
     */
    private String bucketName;

    /**
     * Max upload size in bytes, enforced before calling GCS. Keep in sync with
     * {@code spring.servlet.multipart.max-file-size}.
     */
    private long maxFileSizeBytes = 10L * 1024 * 1024;

    /**
     * Whether {@code OrphanAttachmentCleanupJob} runs at all. Independent of {@link #enabled} in
     * the config file so an environment can turn just the sweep off (e.g. to investigate a false
     * positive) without disabling uploads entirely — but the job itself is always a no-op while
     * {@link #enabled} is {@code false}, since there's no bucket to sweep.
     */
    private boolean orphanCleanupEnabled = true;

    /**
     * How long an uploaded object must sit unreferenced by any {@code Attachment} row before the
     * cleanup job deletes it. Long enough that a shift lead mid-form (or resuming a draft, see the
     * frontend's autosave) is never at risk — this is about reclaiming genuinely abandoned
     * uploads, not policing in-progress ones.
     */
    private long orphanCleanupMinAgeHours = 48;
}
