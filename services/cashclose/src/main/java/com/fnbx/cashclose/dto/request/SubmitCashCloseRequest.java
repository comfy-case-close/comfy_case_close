package com.fnbx.cashclose.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

/** Complete one-step submission. Branch is supplied by X-Branch-Id. */
@Data
public class SubmitCashCloseRequest {
    @NotNull
    private UUID shiftTypeId;
    @NotNull
    private LocalDate businessDate;
    @NotNull
    @Valid
    private ReplaceDenominationsRequest denominations;
    @Valid
    private CashCloseFiguresRequest figures;
    private List<@Valid SubmissionAttachmentRequest> attachments = List.of();
    private List<@Valid AddMovementRequest> movements = List.of();
    private String note;
}
