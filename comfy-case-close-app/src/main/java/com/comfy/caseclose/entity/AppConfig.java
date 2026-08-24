package com.comfy.caseclose.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Singleton row (id always 1, enforced by a DB CHECK constraint) of admin-editable
 * reconciliation thresholds, feature flags and bill-repayment details. Used to live only
 * in AppCashCloseProperties (application.properties), read-only at runtime; this makes
 * it live-editable via ConfigController, matching the legacy GAS "Config" sheet.
 */
@Getter
@Setter
@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    private Short id;

    @Column(name = "diff_allowed_abs", nullable = false)
    private Long diffAllowedAbs;

    @Column(name = "diff_note_required_abs", nullable = false)
    private Long diffNoteRequiredAbs;

    @Column(name = "diff_alert_abs", nullable = false)
    private Long diffAlertAbs;

    @Column(name = "expense_alert_abs", nullable = false)
    private Long expenseAlertAbs;

    @Column(name = "withdrawal_alert_abs", nullable = false)
    private Long withdrawalAlertAbs;

    @Column(name = "default_target_cash_remaining", nullable = false)
    private Long defaultTargetCashRemaining;

    @Column(name = "default_cash_remaining_tolerance", nullable = false)
    private Long defaultCashRemainingTolerance;

    @Column(name = "require_pos_image", nullable = false)
    private Boolean requirePosImage;

    @Column(name = "require_cash_image", nullable = false)
    private Boolean requireCashImage;

    @Column(name = "require_unpaid_bill_repayment", nullable = false)
    private Boolean requireUnpaidBillRepayment;

    @Column(name = "require_expense_receipt_image", nullable = false)
    private Boolean requireExpenseReceiptImage;

    @Column(name = "bill_repayment_bank_name", nullable = false, length = 100)
    private String billRepaymentBankName;

    @Column(name = "bill_repayment_account_number", nullable = false, length = 50)
    private String billRepaymentAccountNumber;

    @Column(name = "bill_repayment_account_name", nullable = false, length = 100)
    private String billRepaymentAccountName;

    @Column(name = "bill_repayment_transfer_prefix", nullable = false, length = 100)
    private String billRepaymentTransferPrefix;

    @Column(name = "session_ttl_hours", nullable = false)
    private Integer sessionTtlHours;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
