package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.FundWithdrawalRequest;
import com.comfy.caseclose.dto.response.FundWithdrawalPotDTO;

import java.time.LocalDate;

public interface FundWithdrawalService {

    FundWithdrawalPotDTO getPotData(Long branchId, LocalDate fromDate, LocalDate toDate, String periodType);

    FundWithdrawalPotDTO record(FundWithdrawalRequest request);
}
