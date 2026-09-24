package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.CashCloseSubmitRequest;
import com.comfy.caseclose.dto.request.CashCloseUpdateRequest;
import com.comfy.caseclose.dto.response.CarryForwardDTO;
import com.comfy.caseclose.dto.response.CashCloseResponseDTO;
import com.comfy.caseclose.dto.response.CashDenominationResponseDTO;
import com.comfy.caseclose.dto.response.CashDiffExplanationResponseDTO;
import com.comfy.caseclose.dto.response.CashMovementResponseDTO;
import com.comfy.caseclose.dto.response.DaySummaryDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.TipResponseDTO;
import com.comfy.caseclose.service.CashCloseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-closes")
@RequiredArgsConstructor
public class CashCloseController {

    private final CashCloseService cashCloseService;

    @GetMapping
    public ResponseEntity<PagedResponse<CashCloseResponseDTO>> listCashCloses(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long shiftTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // No Sort means Postgres doesn't guarantee row order across identical queries — an UPDATE
        // rewrites the row as a new heap tuple (MVCC), which a sequential scan with no ORDER BY can
        // then return near the end, landing an edited close on the last page. Sorted by submittedAt
        // (not updatedAt) so an edit never reorders the list — only a genuinely new submission does.
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "submittedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(
                cashCloseService.listCashCloses(
                        branchId, shiftTypeId, fromDate, toDate, status, pageable));
    }

    @PostMapping
    public ResponseEntity<CashCloseResponseDTO> submitCashClose(@Valid @RequestBody CashCloseSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashCloseService.submitCashClose(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CashCloseResponseDTO> updateCashClose(
            @PathVariable Long id, @Valid @RequestBody CashCloseUpdateRequest request) {
        return ResponseEntity.ok(cashCloseService.updateCashClose(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashCloseResponseDTO> getCashCloseById(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getCashCloseById(id));
    }

    @GetMapping("/{id}/denominations")
    public ResponseEntity<List<CashDenominationResponseDTO>> getDenominations(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getDenominations(id));
    }

    @GetMapping("/{id}/movements")
    public ResponseEntity<List<CashMovementResponseDTO>> getMovements(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getCashCloseById(id).getMovements());
    }

    @GetMapping("/{id}/tips")
    public ResponseEntity<List<TipResponseDTO>> getTips(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getTips(id));
    }

    @GetMapping("/{id}/explanations")
    public ResponseEntity<List<CashDiffExplanationResponseDTO>> getExplanations(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getCashCloseById(id).getExplanations());
    }

    @GetMapping("/{id}/day-summary")
    public ResponseEntity<DaySummaryDTO> getDaySummary(@PathVariable Long id) {
        return ResponseEntity.ok(cashCloseService.getDaySummary(id));
    }

    @GetMapping("/carry-forward")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<CarryForwardDTO> getCarryForward(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam Long shiftTypeId) {
        return ResponseEntity.ok(cashCloseService.getCarryForward(branchId, businessDate, shiftTypeId));
    }
}
