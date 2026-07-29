package com.comfy.caseclose.controller;

import com.comfy.caseclose.config.AppCashCloseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppCashCloseProperties config;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("DIFF_ALLOWED_ABS", config.getDiffAllowedAbs());
        body.put("DIFF_NOTE_REQUIRED_ABS", config.getDiffNoteRequiredAbs());
        body.put("DIFF_ALERT_ABS", config.getDiffAlertAbs());
        body.put("EXPENSE_ALERT_ABS", config.getExpenseAlertAbs());
        body.put("WITHDRAWAL_ALERT_ABS", config.getWithdrawalAlertAbs());
        body.put("DEFAULT_TARGET_CASH_REMAINING", config.getDefaultTargetCashRemaining());
        body.put("DEFAULT_CASH_REMAINING_TOLERANCE", config.getDefaultCashRemainingTolerance());
        body.put("REQUIRE_POS_IMAGE", config.isRequirePosImage());
        body.put("REQUIRE_CASH_IMAGE", config.isRequireCashImage());
        body.put("REQUIRE_UNPAID_BILL_REPAYMENT", config.isRequireUnpaidBillRepayment());
        body.put("BILL_REPAYMENT_BANK_NAME", config.getBillRepaymentBankName());
        body.put("BILL_REPAYMENT_ACCOUNT_NUMBER", config.getBillRepaymentAccountNumber());
        body.put("BILL_REPAYMENT_ACCOUNT_NAME", config.getBillRepaymentAccountName());
        body.put("BILL_REPAYMENT_TRANSFER_PREFIX", config.getBillRepaymentTransferPrefix());
        body.put("REQUIRE_EXPENSE_RECEIPT_IMAGE", config.isRequireExpenseReceiptImage());
        body.put("SESSION_TTL_HOURS", config.getSessionTtlHours());
        return ResponseEntity.ok(body);
    }
}
