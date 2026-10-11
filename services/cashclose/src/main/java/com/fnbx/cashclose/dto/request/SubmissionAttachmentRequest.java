package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** An uploaded file linked to the close as part of one-step submission. Movement
 * receipts are not listed here: a movement references its file as receiptFileId. */
public record SubmissionAttachmentRequest(@NotNull UUID fileId) {}
