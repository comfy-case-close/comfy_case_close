package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateConfigRequest {

    @NotNull(message = "diffAllowedAbs is required")
    @PositiveOrZero(message = "diffAllowedAbs must be zero or positive")
    private Long diffAllowedAbs;

    @NotNull(message = "diffNoteRequiredAbs is required")
    @PositiveOrZero(message = "diffNoteRequiredAbs must be zero or positive")
    private Long diffNoteRequiredAbs;

    @NotNull(message = "diffAlertAbs is required")
    @PositiveOrZero(message = "diffAlertAbs must be zero or positive")
    private Long diffAlertAbs;

    @NotNull(message = "expenseAlertAbs is required")
    @PositiveOrZero(message = "expenseAlertAbs must be zero or positive")
    private Long expenseAlertAbs;

    @NotNull(message = "withdrawalAlertAbs is required")
    @PositiveOrZero(message = "withdrawalAlertAbs must be zero or positive")
    private Long withdrawalAlertAbs;

    @NotNull(message = "defaultTargetCashRemaining is required")
    @PositiveOrZero(message = "defaultTargetCashRemaining must be zero or positive")
    private Long defaultTargetCashRemaining;

    @NotNull(message = "defaultCashRemainingTolerance is required")
    @PositiveOrZero(message = "defaultCashRemainingTolerance must be zero or positive")
    private Long defaultCashRemainingTolerance;

    @NotNull(message = "requirePosImage is required")
    private Boolean requirePosImage;

    @NotNull(message = "requireCashImage is required")
    private Boolean requireCashImage;

    @NotNull(message = "requireUnpaidBillRepayment is required")
    private Boolean requireUnpaidBillRepayment;

    @NotNull(message = "requireExpenseReceiptImage is required")
    private Boolean requireExpenseReceiptImage;

    @NotBlank(message = "billRepaymentBankName is required")
    private String billRepaymentBankName;

    @NotBlank(message = "billRepaymentAccountNumber is required")
    private String billRepaymentAccountNumber;

    @NotBlank(message = "billRepaymentAccountName is required")
    private String billRepaymentAccountName;

    @NotBlank(message = "billRepaymentTransferPrefix is required")
    private String billRepaymentTransferPrefix;

    @NotNull(message = "sessionTtlHours is required")
    @Min(value = 1, message = "sessionTtlHours must be at least 1")
    private Integer sessionTtlHours;
}
