package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.request.*;
import com.fnbx.cashclose.dto.response.FundWithdrawalResponse;
import com.fnbx.cashclose.enums.FundStatus;
import com.fnbx.cashclose.service.FundWithdrawalService;
import com.fnbx.shared.security.BranchHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/fund-withdrawals")
@RequiredArgsConstructor
public class FundWithdrawalController {
    private final FundWithdrawalService service;

    @GetMapping
    public Page<FundWithdrawalResponse> list(@RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam(required = false) UUID cashCloseId,
            @RequestParam(required = false) UUID transferId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) FundStatus status,
            @RequestParam(defaultValue = "false") boolean includeHistory,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(branchId, cashCloseId, transferId, fromDate, toDate, status, includeHistory, pageable);
    }
    @GetMapping("/{id}")
    public FundWithdrawalResponse get(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return service.get(branchId, id);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FundWithdrawalResponse record(@RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @RequestBody FundWithdrawalRequest request) { return service.record(branchId, request); }

    @PostMapping("/{id}/corrections") @ResponseStatus(HttpStatus.CREATED)
    public FundWithdrawalResponse correct(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id,
            @Valid @RequestBody CorrectFundWithdrawalRequest request) { return service.correct(branchId, id, request); }

    @PostMapping("/{id}/confirm")
    public FundWithdrawalResponse confirm(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return service.confirm(branchId, id);
    }
    @PostMapping("/{id}/reject")
    public FundWithdrawalResponse reject(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id,
            @Valid @RequestBody RejectFundWithdrawalRequest request) { return service.reject(branchId, id, request.reason()); }
}
