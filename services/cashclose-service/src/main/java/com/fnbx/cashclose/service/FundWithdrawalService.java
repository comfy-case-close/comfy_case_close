package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.request.*;
import com.fnbx.cashclose.dto.response.FundWithdrawalResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.enums.FundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface FundWithdrawalService {
    Page<FundWithdrawalResponse> list(UUID branchId, UUID cashCloseId, UUID transferId,
            LocalDate fromDate, LocalDate toDate, FundStatus status, boolean includeHistory, Pageable pageable);
    FundWithdrawalResponse get(UUID branchId, UUID id);
    FundWithdrawalResponse record(UUID branchId, FundWithdrawalRequest request);
    FundWithdrawalResponse correct(UUID branchId, UUID id, CorrectFundWithdrawalRequest request);
    FundWithdrawalResponse confirm(UUID branchId, UUID id);
    FundWithdrawalResponse reject(UUID branchId, UUID id, String reason);
    Map<String, Object> describeCloseCorrection(CashClose close, CashCloseFiguresRequest figures);
    void syncCloseWithdrawal(CashClose close, CashCloseFiguresRequest figures, String editReason);
    void requireConfirmed(CashClose close);
}
