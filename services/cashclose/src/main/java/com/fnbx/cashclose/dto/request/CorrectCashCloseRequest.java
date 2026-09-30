package com.fnbx.cashclose.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Reviewer correction; omitted figures and count remain unchanged. */
@Data
public class CorrectCashCloseRequest {
    @NotBlank
    private String editReason;
    @Valid
    private ReplaceDenominationsRequest denominations;
    @Valid
    private CashCloseFiguresRequest figures;
    /** Null leaves the original note unchanged; empty string clears it. */
    private String note;
}
