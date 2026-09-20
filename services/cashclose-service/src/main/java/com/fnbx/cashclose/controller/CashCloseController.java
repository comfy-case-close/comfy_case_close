package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.CloseDecisionRequest;
import com.fnbx.cashclose.dto.request.MovementDecisionRequest;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.dto.request.SubmitRequest;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.response.CashCloseResponse;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.CloseDecisionResponse;
import com.fnbx.cashclose.dto.response.MovementDecisionResponse;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * HTTP layer only - no business logic.
 *
 * <h2>Two things that never appear here</h2>
 * <ol>
 *   <li><b>businessId in a path or parameter.</b> It comes from the verified JWT via
 *       {@code VerifiedTenantFilter}. A URL like {@code /businesses/{businessId}/closes}
 *       lets a user choose which tenant they are.</li>
 *   <li><b>Direct repository calls.</b> The ArchUnit rule
 *       {@code controllerMustNotTouchRepository} blocks that on every build.</li>
 * </ol>
 *
 * <p>The service returns DTOs, so no entity ever reaches this class - which is
 * also what {@code controllerMustNotExposeEntity} enforces.
 */
@RestController
@RequestMapping("/cash-closes")
@RequiredArgsConstructor
@Validated
public class CashCloseController {

    private final CashCloseService cashCloseService;

    // ---- lifecycle ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<CashCloseResponse> openDraft(
            @Valid @RequestBody OpenDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashCloseService.openDraft(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CashCloseResponse>> listCashCloses(
            @Valid @ModelAttribute CashCloseListFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "businessDate").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(cashCloseService.listCashCloses(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashCloseResponse> getCashCloseById(@PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getById(id));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<CashCloseResponse> submit(
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRequest request) {
        return ResponseEntity.ok(
                cashCloseService.submit(id, request == null ? null : request.getNote()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<CashCloseResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) CloseDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.approve(id, request == null ? null : request.getNote()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<CashCloseResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody CloseDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.reject(id, request.getNote()));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<CashCloseResponse> reopen(
            @PathVariable UUID id,
            @Valid @RequestBody CloseDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.reopen(id, request.getNote()));
    }

    // ---- cash ledger -------------------------------------------------------

    @GetMapping("/movements")
    public ResponseEntity<PagedResponse<CashMovementResponse>> listMovements(
            @Valid @ModelAttribute CashMovementListFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(cashCloseService.listMovements(filter, pageable));
    }

    @GetMapping("/{id}/movements")
    public ResponseEntity<List<CashMovementResponse>> getMovements(@PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getMovements(id));
    }

    /** {@code amount} is always positive; the movement kind decides the sign. */
    @PostMapping("/{id}/movements")
    public ResponseEntity<CashMovementResponse> addMovement(
            @PathVariable UUID id,
            @Valid @RequestBody AddMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashCloseService.addMovement(id, request));
    }

    /** Corrects a line that is still PENDING. Updates in place, never inserts. */
    @PatchMapping("/movements/{movementId}")
    public ResponseEntity<CashMovementResponse> updateMovement(
            @PathVariable UUID movementId,
            @Valid @RequestBody UpdateMovementRequest request) {
        return ResponseEntity.ok(cashCloseService.updateMovement(movementId, request));
    }

    @PostMapping("/movements/{movementId}/approve")
    public ResponseEntity<CashMovementResponse> approveMovement(
            @PathVariable UUID movementId,
            @RequestBody(required = false) MovementDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.approveMovement(movementId, request == null ? null : request.getNote()));
    }

    @PostMapping("/movements/{movementId}/reject")
    public ResponseEntity<CashMovementResponse> rejectMovement(
            @PathVariable UUID movementId,
            @Valid @RequestBody MovementDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.rejectMovement(movementId, request.getNote()));
    }

    /** Sends a decided line back to PENDING so it can be corrected. Audited. */
    @PostMapping("/movements/{movementId}/reopen")
    public ResponseEntity<CashMovementResponse> reopenMovement(
            @PathVariable UUID movementId,
            @RequestBody(required = false) MovementDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.reopenMovement(movementId, request == null ? null : request.getNote()));
    }

    // ---- history -----------------------------------------------------------

    /** Decisions on the close itself, including every reviewer comment. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<CloseDecisionResponse>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getHistory(id));
    }

    /** Decisions on every ledger line, each carrying the amount it endorsed. */
    @GetMapping("/{id}/movement-history")
    public ResponseEntity<List<MovementDecisionResponse>> getMovementHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getMovementHistory(id));
    }
}
