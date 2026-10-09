package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarkPaidRequest(@NotEmpty List<UUID> paymentIds, Instant paidAt, String bankReference) {
}
