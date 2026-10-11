package com.fnbx.cashclose.dto.response;

import java.util.List;
import java.util.UUID;

/** Every file of a close: its own attachments followed by its movements' receipts. */
public record CloseAttachmentResponse(UUID cashCloseId, List<AttachedFileResponse> files) {}
