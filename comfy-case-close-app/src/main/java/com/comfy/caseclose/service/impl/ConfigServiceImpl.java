package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.UpdateConfigRequest;
import com.comfy.caseclose.entity.AppConfig;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.AppConfigRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    /** Singleton row — DB CHECK constraint (ck_app_config_singleton) enforces id = 1. */
    private static final short SINGLETON_ID = 1;

    private final AppConfigRepository appConfigRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        return toMap(findSingleton());
    }

    @Override
    @Transactional
    public Map<String, Object> updateConfig(UpdateConfigRequest request) {
        AppConfig config = findSingleton();
        config.setDiffAllowedAbs(request.getDiffAllowedAbs());
        config.setDiffNoteRequiredAbs(request.getDiffNoteRequiredAbs());
        config.setDiffAlertAbs(request.getDiffAlertAbs());
        config.setExpenseAlertAbs(request.getExpenseAlertAbs());
        config.setWithdrawalAlertAbs(request.getWithdrawalAlertAbs());
        config.setDefaultTargetCashRemaining(request.getDefaultTargetCashRemaining());
        config.setDefaultCashRemainingTolerance(request.getDefaultCashRemainingTolerance());
        config.setRequirePosImage(request.getRequirePosImage());
        config.setRequireCashImage(request.getRequireCashImage());
        config.setRequireUnpaidBillRepayment(request.getRequireUnpaidBillRepayment());
        config.setRequireExpenseReceiptImage(request.getRequireExpenseReceiptImage());
        config.setBillRepaymentBankName(request.getBillRepaymentBankName());
        config.setBillRepaymentAccountNumber(request.getBillRepaymentAccountNumber());
        config.setBillRepaymentAccountName(request.getBillRepaymentAccountName());
        config.setBillRepaymentTransferPrefix(request.getBillRepaymentTransferPrefix());
        config.setSessionTtlHours(request.getSessionTtlHours());
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy(userRepository.findById(SecurityUtils.currentUserId()).orElse(null));

        return toMap(appConfigRepository.save(config));
    }

    private AppConfig findSingleton() {
        return appConfigRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("App config row is missing — re-run the app_config migration"));
    }

    private Map<String, Object> toMap(AppConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("DIFF_ALLOWED_ABS", config.getDiffAllowedAbs());
        body.put("DIFF_NOTE_REQUIRED_ABS", config.getDiffNoteRequiredAbs());
        body.put("DIFF_ALERT_ABS", config.getDiffAlertAbs());
        body.put("EXPENSE_ALERT_ABS", config.getExpenseAlertAbs());
        body.put("WITHDRAWAL_ALERT_ABS", config.getWithdrawalAlertAbs());
        body.put("DEFAULT_TARGET_CASH_REMAINING", config.getDefaultTargetCashRemaining());
        body.put("DEFAULT_CASH_REMAINING_TOLERANCE", config.getDefaultCashRemainingTolerance());
        body.put("REQUIRE_POS_IMAGE", config.getRequirePosImage());
        body.put("REQUIRE_CASH_IMAGE", config.getRequireCashImage());
        body.put("REQUIRE_UNPAID_BILL_REPAYMENT", config.getRequireUnpaidBillRepayment());
        body.put("BILL_REPAYMENT_BANK_NAME", config.getBillRepaymentBankName());
        body.put("BILL_REPAYMENT_ACCOUNT_NUMBER", config.getBillRepaymentAccountNumber());
        body.put("BILL_REPAYMENT_ACCOUNT_NAME", config.getBillRepaymentAccountName());
        body.put("BILL_REPAYMENT_TRANSFER_PREFIX", config.getBillRepaymentTransferPrefix());
        body.put("REQUIRE_EXPENSE_RECEIPT_IMAGE", config.getRequireExpenseReceiptImage());
        body.put("SESSION_TTL_HOURS", config.getSessionTtlHours());
        return body;
    }
}
