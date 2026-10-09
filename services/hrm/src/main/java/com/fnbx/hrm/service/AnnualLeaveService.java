package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.AnnualLeaveQuotaRequest;
import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.AnnualLeaveQuotaResponse;
import java.util.UUID;

public interface AnnualLeaveService {

    AnnualLeaveQuotaResponse getQuota(UUID staffId, short year);

    AnnualLeaveQuotaResponse setQuota(UUID staffId, short year, AnnualLeaveQuotaRequest request);

    AnnualLeaveBalanceResponse getBalance(UUID staffId, short year);
}
