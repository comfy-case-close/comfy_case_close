package com.fnbx.cashclose.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One file of a close, with the stored file's details.
 *
 * <p>A close attachment carries {@code attachmentId}; a movement receipt carries
 * {@code movementId} instead, and its {@code attachedBy} is the staff who recorded
 * that movement.
 */
public record AttachedFileResponse(UUID attachmentId, UUID movementId, UUID cashCloseId, UUID fileId,
                                   String fileKind, String publicUrl, String provider, String contentType,
                                   UUID uploadedBy, Instant uploadedAt, UUID attachedBy) {}
