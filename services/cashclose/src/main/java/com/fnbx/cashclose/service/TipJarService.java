package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.request.TipPayoutRequest;
import com.fnbx.cashclose.dto.response.TipJarResponse;
import com.fnbx.cashclose.dto.response.TipPayoutResultResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface TipJarService {
    TipJarResponse getTipJar(UUID branchId, LocalDate fromDate, LocalDate toDate);
    TipPayoutResultResponse recordPayout(UUID branchId, TipPayoutRequest request);
}
