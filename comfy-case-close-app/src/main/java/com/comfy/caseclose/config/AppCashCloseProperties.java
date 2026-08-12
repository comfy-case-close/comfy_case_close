package com.comfy.caseclose.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reconciliation thresholds, feature flags and bill-repayment details. These are the values the
 * legacy GAS "Config" sheet held; per database.md (Round 3) they live in application config, not a
 * DB table. Overridable per environment via app.cash-close.* properties / env vars.
 */
@Component
@ConfigurationProperties(prefix = "app.cash-close")
@Data
public class AppCashCloseProperties {

    private long diffAllowedAbs = 10_000;
    private long diffNoteRequiredAbs = 50_000;
    private long diffAlertAbs = 100_000;
    private long expenseAlertAbs = 500_000;
    private long withdrawalAlertAbs = 3_000_000;
    private long defaultTargetCashRemaining = 3_000_000;
    private long defaultCashRemainingTolerance = 200_000;

    private boolean requirePosImage = false;
    private boolean requireCashImage = false;
    private boolean requireUnpaidBillRepayment = true;
    private boolean requireExpenseReceiptImage = false;

    private String billRepaymentBankName = "MB Bank";
    private String billRepaymentAccountNumber = "6660104420";
    private String billRepaymentAccountName = "COMFY";
    private String billRepaymentTransferPrefix = "BU BILL COMFY";

    private int sessionTtlHours = 12;
}
