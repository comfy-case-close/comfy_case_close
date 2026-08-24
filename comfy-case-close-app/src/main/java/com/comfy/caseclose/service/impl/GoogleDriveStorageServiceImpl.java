//package com.comfy.caseclose.service.impl;
//
//import com.comfy.caseclose.config.GoogleDriveProperties;
//import com.comfy.caseclose.exception.BadRequestException;
//import com.comfy.caseclose.service.DriveStorageService;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//import com.google.api.client.http.InputStreamContent;
//import com.google.api.client.json.gson.GsonFactory;
//import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.DriveScopes;
//import com.google.api.services.drive.model.File;
//import com.google.api.services.drive.model.FileList;
//import com.google.api.services.drive.model.Permission;
//import com.google.auth.http.HttpCredentialsAdapter;
//import com.google.auth.oauth2.GoogleCredentials;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.security.GeneralSecurityException;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Collections;
//import java.util.List;
//import java.util.Locale;
//import java.util.UUID;
//
///**
// * Google Drive-backed {@link DriveStorageService}.
// *
// * <p>Folder layout mirrors the legacy GAS build exactly (see
// * {@code comfy_cash_close_package/Code.gs}: {@code getUploadFolder_()}):
// *
// * <pre>{@code <rootFolderId>/<yyyy>/<MM>/<branchCode>/}</pre>
// *
// * <p>Timezone for the year/month split is {@code Asia/Ho_Chi_Minh}, same as
// * {@code APP.timezone} in the legacy build, so files land in the same monthly bucket a
// * human would expect from the old system.
// *
// * <p><b>Visibility:</b> the legacy build never set explicit per-file permissions — it relied
// * on staff already having domain access to the owner's Google Drive. This backend instead
// * grants each uploaded file "anyone with the link can view" so the frontend can render it
// * directly as an {@code <img>} without asking every viewer to be individually added to the
// * Drive folder. If that's unacceptable for a given deployment, tighten
// * {@link #shareReadOnlyWithAnyone(Drive, String)} to per-user sharing instead.
// */
//@Service
//@RequiredArgsConstructor
//public class GoogleDriveStorageServiceImpl implements DriveStorageService {
//
//    private static final Logger log = LoggerFactory.getLogger(GoogleDriveStorageServiceImpl.class);
//    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
//    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
//
//    private final GoogleDriveProperties properties;
//
//    private volatile Drive driveClient;
//
//    @PostConstruct
//    void logConfigState() {
//        if (!properties.isEnabled()) {
//            log.warn("Google Drive attachment storage is DISABLED (app.drive.enabled=false). "
//                    + "Attachment uploads will be rejected until credentials are configured.");
//        }
//    }
//
//    @Override
//    public UploadedFile uploadFile(MultipartFile file, String branchCode, String namePrefix) {
//        requireEnabled();
//        if (file == null || file.isEmpty()) {
//            throw new BadRequestException("File is required for attachment upload.");
//        }
//        if (file.getSize() > properties.getMaxFileSizeBytes()) {
//            throw new BadRequestException(
//                    "File exceeds the maximum allowed size of " + properties.getMaxFileSizeBytes() + " bytes.");
//        }
//
//        Drive drive = client();
//        try {
//            String folderId = getOrCreateUploadFolder(drive, branchCode);
//            String fileName = buildFileName(namePrefix, file);
//
//            File metadata = new File();
//            metadata.setName(fileName);
//            metadata.setParents(Collections.singletonList(folderId));
//
//            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
//            try (InputStream in = file.getInputStream()) {
//                InputStreamContent content = new InputStreamContent(contentType, in);
//                content.setLength(file.getSize());
//
//                File created = drive.files().create(metadata, content)
//                        .setFields("id, name, webViewLink, webContentLink")
//                        .execute();
//
//                shareReadOnlyWithAnyone(drive, created.getId());
//
//                String viewableUrl = created.getWebContentLink() != null
//                        ? created.getWebContentLink()
//                        : created.getWebViewLink();
//
//                return new UploadedFile(created.getId(), viewableUrl, created.getName());
//            }
//        } catch (IOException e) {
//            log.error("Failed to upload attachment to Google Drive", e);
//            throw new BadRequestException("Could not upload file to Google Drive: " + e.getMessage());
//        }
//    }
//
//    @Override
//    public void deleteFile(String fileId) {
//        if (fileId == null || fileId.isBlank()) {
//            return;
//        }
//        requireEnabled();
//        try {
//            client().files().delete(fileId).execute();
//        } catch (IOException e) {
//            // Best-effort: an attachment row can still be deleted from the DB even if the
//            // underlying Drive file is already gone or unreachable.
//            log.warn("Could not delete Drive file {}: {}", fileId, e.getMessage());
//        }
//    }
//
//    // ----- folder tree (mirrors legacy getUploadFolder_ / getOrCreateFolder_) ---------------------
//
//    private String getOrCreateUploadFolder(Drive drive, String branchCode) throws IOException {
//        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
//        String year = now.format(DateTimeFormatter.ofPattern("yyyy", Locale.ROOT));
//        String month = now.format(DateTimeFormatter.ofPattern("MM", Locale.ROOT));
//        String branchSegment = (branchCode == null || branchCode.isBlank()) ? "UNKNOWN" : branchCode.trim();
//
//        String yearFolderId = getOrCreateFolder(drive, properties.getRootFolderId(), year);
//        String monthFolderId = getOrCreateFolder(drive, yearFolderId, month);
//        return getOrCreateFolder(drive, monthFolderId, branchSegment);
//    }
//
//    private String getOrCreateFolder(Drive drive, String parentId, String name) throws IOException {
//        String escapedName = name.replace("\\", "\\\\").replace("'", "\\'");
//        String query = "'" + parentId + "' in parents"
//                + " and name = '" + escapedName + "'"
//                + " and mimeType = '" + FOLDER_MIME_TYPE + "'"
//                + " and trashed = false";
//
//        FileList existing = drive.files().list()
//                .setQ(query)
//                .setSpaces("drive")
//                .setFields("files(id, name)")
//                .setPageSize(1)
//                .execute();
//
//        List<File> matches = existing.getFiles();
//        if (matches != null && !matches.isEmpty()) {
//            return matches.get(0).getId();
//        }
//
//        File folder = new File();
//        folder.setName(name);
//        folder.setMimeType(FOLDER_MIME_TYPE);
//        folder.setParents(Collections.singletonList(parentId));
//        File created = drive.files().create(folder).setFields("id").execute();
//        return created.getId();
//    }
//
//    private void shareReadOnlyWithAnyone(Drive drive, String fileId) {
//        Permission permission = new Permission().setType("anyone").setRole("reader");
//        try {
//            drive.permissions().create(fileId, permission)
//                    .setFields("id")
//                    .execute();
//        } catch (IOException e) {
//            // Non-fatal: the file is still uploaded and reachable by anyone with folder-level
//            // access; only the "share with anyone" convenience link failed.
//            log.warn("Could not set public-read permission on Drive file {}: {}", fileId, e.getMessage());
//        }
//    }
//
//    private String buildFileName(String namePrefix, MultipartFile file) {
//        String safePrefix = (namePrefix == null || namePrefix.isBlank())
//                ? "ATTACHMENT"
//                : namePrefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
//        String extension = extensionFor(file);
//        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
//        return safePrefix + "_" + System.currentTimeMillis() + "_" + uniqueSuffix + extension;
//    }
//
//    private String extensionFor(MultipartFile file) {
//        String originalName = file.getOriginalFilename();
//        if (originalName != null && originalName.contains(".")) {
//            return originalName.substring(originalName.lastIndexOf('.'));
//        }
//        String contentType = file.getContentType();
//        if (contentType == null) {
//            return "";
//        }
//        return switch (contentType) {
//            case "image/jpeg" -> ".jpg";
//            case "image/png" -> ".png";
//            case "image/webp" -> ".webp";
//            case "image/heic" -> ".heic";
//            case "application/pdf" -> ".pdf";
//            default -> "";
//        };
//    }
//
//    // ----- client bootstrap -------------------------------------------------------------------------
//
//    private void requireEnabled() {
//        if (!properties.isEnabled()) {
//            throw new BadRequestException(
//                    "Attachment upload is not configured yet. Set app.drive.enabled=true, "
//                            + "app.drive.service-account-key-path and app.drive.root-folder-id "
//                            + "(see GoogleDriveProperties for setup steps).");
//        }
//        if (properties.getRootFolderId() == null || properties.getRootFolderId().isBlank()) {
//            throw new BadRequestException("app.drive.root-folder-id is not configured.");
//        }
//    }
//
//    private Drive client() {
//        Drive existing = driveClient;
//        if (existing != null) {
//            return existing;
//        }
//        synchronized (this) {
//            if (driveClient == null) {
//                driveClient = buildClient();
//            }
//            return driveClient;
//        }
//    }
//
//    private Drive buildClient() {
//        String keyPath = properties.getServiceAccountKeyPath();
//        if (keyPath == null || keyPath.isBlank()) {
//            throw new BadRequestException("app.drive.service-account-key-path is not configured.");
//        }
//        try (InputStream credentialsStream = new FileInputStream(keyPath)) {
//            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
//                    .createScoped(Collections.singletonList(DriveScopes.DRIVE));
//
//            return new Drive.Builder(
//                    GoogleNetHttpTransport.newTrustedTransport(),
//                    GsonFactory.getDefaultInstance(),
//                    new HttpCredentialsAdapter(credentials))
//                    .setApplicationName(properties.getApplicationName())
//                    .build();
//        } catch (IOException e) {
//            throw new BadRequestException(
//                    "Could not read Google service account key at '" + keyPath + "': " + e.getMessage());
//        } catch (GeneralSecurityException e) {
//            throw new BadRequestException("Could not initialize Google Drive client: " + e.getMessage());
//        }
//    }
//}
