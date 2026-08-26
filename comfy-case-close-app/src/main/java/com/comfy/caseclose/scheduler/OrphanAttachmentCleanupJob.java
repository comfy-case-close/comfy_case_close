package com.comfy.caseclose.scheduler;

import com.comfy.caseclose.config.GoogleCloudStorageProperties;
import com.comfy.caseclose.repository.AttachmentRepository;
import com.comfy.caseclose.service.AttachmentStorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reclaims attachments that were uploaded to GCS but never made it into a submitted cash close.
 *
 * <p>{@code POST /attachments/upload} writes the object to the bucket immediately and returns a
 * URL — no {@code Attachment} row exists until the whole cash close is submitted (see
 * {@code AttachmentController}'s Javadoc). A shift lead who picks photos and then abandons or
 * refreshes the form leaves those files behind with nothing ever pointing at them; nothing
 * previously reclaimed them.
 *
 * <p>Deliberately conservative: only ever deletes an object that is (a) older than
 * {@link GoogleCloudStorageProperties#getOrphanCleanupMinAgeHours()}, giving any in-progress form
 * — including a resumed autosave draft on the frontend — a wide margin, and (b) not referenced by
 * any {@code Attachment.file_url} in the database at the moment the sweep runs. A file uploaded
 * and then genuinely submitted is referenced and never touched.
 */
@Component
@RequiredArgsConstructor
public class OrphanAttachmentCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanAttachmentCleanupJob.class);

    private final AttachmentStorageService attachmentStorageService;
    private final AttachmentRepository attachmentRepository;
    private final GoogleCloudStorageProperties properties;

    @Scheduled(cron = "${app.storage.orphan-cleanup-cron:0 0 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void cleanupOrphans() {
        if (!properties.isEnabled() || !properties.isOrphanCleanupEnabled()) {
            return;
        }

        List<AttachmentStorageService.StoredObject> candidates =
                attachmentStorageService.listObjectsOlderThan(Duration.ofHours(properties.getOrphanCleanupMinAgeHours()));
        if (candidates.isEmpty()) {
            return;
        }

        Set<String> referencedKeys = referencedObjectKeys();
        int deleted = 0;
        for (AttachmentStorageService.StoredObject candidate : candidates) {
            if (referencedKeys.contains(candidate.objectKey())) {
                continue;
            }
            attachmentStorageService.deleteFile(candidate.objectKey());
            deleted++;
        }

        log.info("Orphan attachment cleanup: removed {} of {} candidate object(s) older than {}h",
                deleted, candidates.size(), properties.getOrphanCleanupMinAgeHours());
    }

    private Set<String> referencedObjectKeys() {
        Set<String> keys = new HashSet<>();
        for (String fileUrl : attachmentRepository.findAllFileUrls()) {
            String objectKey = attachmentStorageService.objectKeyFromUrl(fileUrl);
            if (objectKey != null) {
                keys.add(objectKey);
            }
        }
        return keys;
    }
}
