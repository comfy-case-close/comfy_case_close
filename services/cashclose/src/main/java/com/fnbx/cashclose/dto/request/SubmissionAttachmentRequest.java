package com.fnbx.cashclose.dto.request;

import com.fnbx.shared.enums.FileKind;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** An uploaded file linked as part of one-step submission. Supply attachmentId
 * when a movement in the same request references it as receiptAttachmentId. */
public record SubmissionAttachmentRequest(UUID attachmentId, @NotNull UUID fileId,
                                          @NotNull FileKind fileKind) {}
