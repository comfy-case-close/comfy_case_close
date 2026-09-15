package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.CashCloseSubmitRequest;
import com.comfy.caseclose.dto.response.CarryForwardDTO;
import com.comfy.caseclose.dto.response.CashCloseResponseDTO;
import com.comfy.caseclose.dto.response.CashDenominationResponseDTO;
import com.comfy.caseclose.dto.response.DaySummaryDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.TipResponseDTO;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface CashCloseService {

    CashCloseResponseDTO submitCashClose(CashCloseSubmitRequest request);

    CashCloseResponseDTO getCashCloseById(Long id);

    /**
     * @param fromDate     inclusive lower bound on business date, or {@code null}. Ignored when
     *                     {@code businessDate} is given - an exact day is the narrower filter.
     * @param toDate       inclusive upper bound on business date, or {@code null}
     */
    PagedResponse<CashCloseResponseDTO> listCashCloses(
            Long branchId,
            Long shiftTypeId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            Pageable pageable);

    List<CashDenominationResponseDTO> getDenominations(Long cashCloseId);

    List<TipResponseDTO> getTips(Long cashCloseId);

    DaySummaryDTO getDaySummary(Long cashCloseId);

    CarryForwardDTO getCarryForward(Long branchId, LocalDate businessDate, Long shiftTypeId);
}
