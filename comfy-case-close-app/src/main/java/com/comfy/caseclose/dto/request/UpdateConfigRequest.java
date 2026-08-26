package com.comfy.caseclose.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * Wire keys are SCREAMING_SNAKE_CASE, not Jackson's default camelCase: this must accept exactly
 * what GET /api/v1/config emits (see ConfigServiceImpl#toMap), which matches the legacy GAS
 * "Config" sheet's key format on purpose. Without the {@link JsonProperty} overrides, a client
 * that round-trips the GET response straight into this PUT body — which is exactly what the
 * admin config screen does — binds every field to null and fails all validations at once.
 */
@Data
public class UpdateConfigRequest {

    @JsonProperty("DIFF_ALLOWED_ABS")
    @NotNull(message = "diffAllowedAbs is required")
    @PositiveOrZero(message = "diffAllowedAbs must be zero or positive")
    private Long diffAllowedAbs;

    @JsonProperty("DIFF_NOTE_REQUIRED_ABS")
    @NotNull(message = "diffNoteRequiredAbs is required")
    @PositiveOrZero(message = "diffNoteRequiredAbs must be zero or positive")
    private Long diffNoteRequiredAbs;

    @JsonProperty("DIFF_ALERT_ABS")
    @NotNull(message = "diffAlertAbs is required")
    @PositiveOrZero(message = "diffAlertAbs must be zero or positive")
    private Long diffAlertAbs;

    @JsonProperty("EXPENSE_ALERT_ABS")
    @NotNull(message = "expenseAlertAbs is required")
    @PositiveOrZero(message = "expenseAlertAbs must be zero or positive")
    private Long expenseAlertAbs;

    @JsonProperty("WITHDRAWAL_ALERT_ABS")
    @NotNull(message = "withdrawalAlertAbs is required")
    @PositiveOrZero(message = "withdrawalAlertAbs must be zero or positive")
    private Long withdrawalAlertAbs;

    @JsonProperty("DEFAULT_TARGET_CASH_REMAINING")
    @NotNull(message = "defaultTargetCashRemaining is required")
    @PositiveOrZero(message = "defaultTargetCashRemaining must be zero or positive")
    private Long defaultTargetCashRemaining;

    @JsonProperty("DEFAULT_CASH_REMAINING_TOLERANCE")
    @NotNull(message = "defaultCashRemainingTolerance is required")
    @PositiveOrZero(message = "defaultCashRemainingTolerance must be zero or positive")
    private Long defaultCashRemainingTolerance;

    @JsonProperty("REQUIRE_POS_IMAGE")
    @NotNull(message = "requirePosImage is required")
    private Boolean requirePosImage;

    @JsonProperty("REQUIRE_CASH_IMAGE")
    @NotNull(message = "requireCashImage is required")
    private Boolean requireCashImage;

    @JsonProperty("REQUIRE_UNPAID_BILL_REPAYMENT")
    @NotNull(message = "requireUnpaidBillRepayment is required")
    private Boolean requireUnpaidBillRepayment;

    @JsonProperty("REQUIRE_EXPENSE_RECEIPT_IMAGE")
    @NotNull(message = "requireExpenseReceiptImage is required")
    private Boolean requireExpenseReceiptImage;

    @JsonProperty("BILL_REPAYMENT_BANK_NAME")
    @NotBlank(message = "billRepaymentBankName is required")
    private String billRepaymentBankName;

    @JsonProperty("BILL_REPAYMENT_ACCOUNT_NUMBER")
    @NotBlank(message = "billRepaymentAccountNumber is required")
    private String billRepaymentAccountNumber;

    @JsonProperty("BILL_REPAYMENT_ACCOUNT_NAME")
    @NotBlank(message = "billRepaymentAccountName is required")
    private String billRepaymentAccountName;

    @JsonProperty("BILL_REPAYMENT_TRANSFER_PREFIX")
    @NotBlank(message = "billRepaymentTransferPrefix is required")
    private String billRepaymentTransferPrefix;

    @JsonProperty("SESSION_TTL_HOURS")
    @NotNull(message = "sessionTtlHours is required")
    @Min(value = 1, message = "sessionTtlHours must be at least 1")
    private Integer sessionTtlHours;
}
