package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.TipPayoutRequest;
import com.comfy.caseclose.dto.response.TipJarDTO;
import com.comfy.caseclose.dto.response.TipPayoutResultDTO;

import java.time.LocalDate;

public interface TipJarService {

    TipJarDTO getTipJar(Long branchId, LocalDate fromDate, LocalDate toDate);

    TipPayoutResultDTO recordPayout(TipPayoutRequest request);
}
