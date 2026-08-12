package com.comfy.caseclose.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google Drive storage config for cash-close attachments (photos of the POS slip, cash
 * drawer, withdrawal proof, unpaid-bill repayment proof, etc.).
 *
 * <p>Mirrors the legacy GAS build's file-storage behaviour (see
 * {@code comfy_cash_close_package/Code.gs}: {@code APP.uploadRootFolderName},
 * {@code getUploadFolder_()}), but the backend authenticates as a Google Cloud
 * <b>Service Account</b> — Apps Script instead ran under the owner's own Google identity, so
 * it needed no separate credential.
 *
 * <p>Setup (do this once per environment, see the README/AGENTS.md drive section):
 * <ol>
 *   <li>Google Cloud Console → enable the Drive API for the project.</li>
 *   <li>Create a Service Account, download its JSON key. Point
 *       {@code app.drive.service-account-key-path} at that file (or set
 *       {@code GOOGLE_DRIVE_SERVICE_ACCOUNT_KEY} env var). Never commit the key file.</li>
 *   <li>In Google Drive, create (or pick) the root upload folder, share it with the service
 *       account's {@code client_email} as Editor, then set
 *       {@code app.drive.root-folder-id} to that folder's ID (the segment after
 *       {@code /folders/} in its URL).</li>
 * </ol>
 */
@Component
@ConfigurationProperties(prefix = "app.drive")
@Data
public class GoogleDriveProperties {

    /**
     * Whether Drive upload is enabled. When {@code false}, {@code AttachmentService} rejects
     * upload requests with a clear message instead of failing to build a Drive client — lets
     * the rest of the app run before Drive credentials are provisioned.
     */
    private boolean enabled = false;

    /**
     * Absolute path to the Service Account JSON key file. Keep this outside the repo (e.g. a
     * mounted secret, or a path outside the working tree) and point env var
     * {@code GOOGLE_DRIVE_SERVICE_ACCOUNT_KEY} at it in every real environment.
     */
    private String serviceAccountKeyPath;

    /**
     * Drive folder ID of the shared root upload folder (must already be shared with the
     * service account as Editor — a service account cannot grant itself access to a folder it
     * doesn't own). Equivalent to the legacy {@code APP.uploadRootFolderName} root, except the
     * legacy build created that folder itself under the owner's own Drive.
     */
    private String rootFolderId;

    /**
     * Application name sent to the Drive API (shows up in Drive API usage / audit logs).
     */
    private String applicationName = "Comfy Cash Close";

    /**
     * Max upload size in bytes, enforced before calling Drive. Keep in sync with
     * {@code spring.servlet.multipart.max-file-size}.
     */
    private long maxFileSizeBytes = 10L * 1024 * 1024;
}
