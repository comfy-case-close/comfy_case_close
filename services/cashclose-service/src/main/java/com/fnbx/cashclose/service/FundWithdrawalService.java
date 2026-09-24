package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.request.FundWithdrawalRequest;
import com.fnbx.cashclose.dto.response.FundWithdrawalPotResponse;
import com.fnbx.cashclose.enums.FundPeriod;

import java.time.LocalDate;
import java.util.UUID;

public interface FundWithdrawalService {
    FundWithdrawalPotResponse getPot(UUID branchId, LocalDate fromDate, LocalDate toDate, FundPeriod periodType);
    FundWithdrawalPotResponse record(UUID branchId, FundWithdrawalRequest request);
}
