package com.fnbx.cashclose.dto.response;

import java.util.UUID;

public record CloseAttachmentResponse(UUID attachmentId, UUID cashCloseId, UUID fileId, String fileKind) {}
