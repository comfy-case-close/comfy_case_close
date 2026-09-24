package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.AttachFileRequest;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.CloseDecisionRequest;
import com.fnbx.cashclose.dto.request.MovementDecisionRequest;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.dto.request.ReplaceDenominationsRequest;
import com.fnbx.cashclose.dto.request.SubmitRequest;
import com.fnbx.cashclose.dto.request.UpdateCashCloseRequest;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.response.CashCloseResponse;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.CloseAttachmentResponse;
import com.fnbx.cashclose.dto.response.CloseDecisionResponse;
import com.fnbx.cashclose.dto.response.DaySummaryResponse;
import com.fnbx.cashclose.dto.response.DenominationResponse;
import com.fnbx.cashclose.dto.response.DenominationSetResponse;
import com.fnbx.cashclose.dto.response.MovementDecisionResponse;
import com.fnbx.cashclose.dto.response.MovementKindResponse;
import com.fnbx.cashclose.dto.response.ShiftTypeResponse;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.shared.security.BranchHeader;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
 *
 * <h2>{@code X-Branch-Id}</h2>
 * Endpoints that act at a branch take it from the header, never from the body or
 * the path. The controller only reads it; {@code BranchAccessGuard} decides whether
 * the caller may use it, against the live row in {@code identity.staff_branch_role}
 * rather than against the token's claim. A missing or unparseable header is a 400
 * from Spring before any of that - it is a malformed request, not a denied one.
 *
 * <p>The read endpoints below do not take it. They are already scoped by the
 * intersection of signed branches and live assignments. Demanding a branch header would mean a
 * client had to know the answer before asking the question.
 */
@RestController
@RequestMapping("/cash-closes")
@RequiredArgsConstructor
@Validated
public class CashCloseController {

    private final CashCloseService cashCloseService;

    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<CloseAttachmentResponse>> getAttachments(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getAttachments(branchId, id));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<CloseAttachmentResponse> attachFile(
            @RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id,
            @Valid @RequestBody AttachFileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashCloseService.attachFile(branchId, id, request));
    }

    @GetMapping("/shift-types")
    public ResponseEntity<List<ShiftTypeResponse>> listShiftTypes(@RequestHeader(BranchHeader.NAME) UUID branchId) {
        return ResponseEntity.ok(cashCloseService.listShiftTypes(branchId));
    }

    @GetMapping("/denominations")
    public ResponseEntity<List<DenominationResponse>> listDenominations(@RequestHeader(BranchHeader.NAME) UUID branchId) {
        return ResponseEntity.ok(cashCloseService.listDenominations(branchId));
    }

    @GetMapping("/{id}/day-summary")
    public ResponseEntity<DaySummaryResponse> getDaySummary(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getDaySummary(branchId, id));
    }

    // ---- lifecycle ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<CashCloseResponse> openDraft(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @RequestBody OpenDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashCloseService.openDraft(branchId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CashCloseResponse>> listCashCloses(@RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @ModelAttribute CashCloseListFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "businessDate").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(cashCloseService.listCashCloses(branchId, filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashCloseResponse> getCashCloseById(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getById(branchId, id));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<CashCloseResponse> submit(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRequest request) {
        return ResponseEntity.ok(
                cashCloseService.submit(branchId, id, request == null ? null : request.getNote()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<CashCloseResponse> approve(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @RequestBody(required = false) CloseDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.approve(branchId, id, request == null ? null : request.getNote()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<CashCloseResponse> reject(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.reject(branchId, id, request.getNote()));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<CashCloseResponse> reopen(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.reopen(branchId, id, request.getNote()));
    }

    /** The two typed-in figures: the withdrawal, and expected cash when POS is down. */
    @PatchMapping("/{id}")
    public ResponseEntity<CashCloseResponse> updateCashClose(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCashCloseRequest request) {
        return ResponseEntity.ok(cashCloseService.updateCashClose(branchId, id, request));
    }

    /** Retires a close. ADMIN at that branch, and a reason is required. */
    @PostMapping("/{id}/void")
    public ResponseEntity<CashCloseResponse> voidClose(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.voidClose(branchId, id, request.getNote()));
    }

    // ---- denomination count ------------------------------------------------

    @GetMapping("/{id}/denominations")
    public ResponseEntity<DenominationSetResponse> getDenominations(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getDenominations(branchId, id));
    }

    /**
     * Replaces the whole count. PUT, not PATCH, and not POST: counting the drawer is
     * one idempotent act, and re-sending the same body twice must leave the same
     * total rather than double it.
     */
    @PutMapping("/{id}/denominations")
    public ResponseEntity<DenominationSetResponse> replaceDenominations(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceDenominationsRequest request) {
        return ResponseEntity.ok(cashCloseService.replaceDenominations(branchId, id, request));
    }

    // ---- catalogue ---------------------------------------------------------

    /**
     * The movement kinds available on a business date, for the entry dropdown.
     *
     * <p>Mapped above {@code /{id}} on purpose and matched before it, the same way
     * {@code /movements} already is: Spring prefers a literal segment to a variable.
     *
     * <p>{@code businessDate} defaults to today. A close entered late should pass its
     * own date, or it will be offered a vocabulary that did not exist on the shift.
     */
    @GetMapping("/movement-kinds")
    public ResponseEntity<List<MovementKindResponse>> listMovementKinds(@RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ResponseEntity.ok(cashCloseService.listMovementKinds(branchId, businessDate));
    }

    // ---- cash ledger -------------------------------------------------------

    @GetMapping("/movements")
    public ResponseEntity<PagedResponse<CashMovementResponse>> listMovements(@RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @ModelAttribute CashMovementListFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(cashCloseService.listMovements(branchId, filter, pageable));
    }

    @GetMapping("/{id}/movements")
    public ResponseEntity<List<CashMovementResponse>> getMovements(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getMovements(branchId, id));
    }

    /** {@code amount} is always positive; the movement kind decides the sign. */
    @PostMapping("/{id}/movements")
    public ResponseEntity<CashMovementResponse> addMovement(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody AddMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashCloseService.addMovement(branchId, id, request));
    }

    /** Corrects a line that is still PENDING. Updates in place, never inserts. */
    @PatchMapping("/movements/{movementId}")
    public ResponseEntity<CashMovementResponse> updateMovement(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID movementId,
            @Valid @RequestBody UpdateMovementRequest request) {
        return ResponseEntity.ok(cashCloseService.updateMovement(branchId, movementId, request));
    }

    @PostMapping("/movements/{movementId}/approve")
    public ResponseEntity<CashMovementResponse> approveMovement(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID movementId,
            @RequestBody(required = false) MovementDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.approveMovement(branchId, movementId, request == null ? null : request.getNote()));
    }

    @PostMapping("/movements/{movementId}/reject")
    public ResponseEntity<CashMovementResponse> rejectMovement(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID movementId,
            @Valid @RequestBody MovementDecisionRequest request) {
        return ResponseEntity.ok(cashCloseService.rejectMovement(branchId, movementId, request.getNote()));
    }

    /** Sends a decided line back to PENDING so it can be corrected. Audited. */
    @PostMapping("/movements/{movementId}/reopen")
    public ResponseEntity<CashMovementResponse> reopenMovement(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID movementId,
            @RequestBody(required = false) MovementDecisionRequest request) {
        return ResponseEntity.ok(
                cashCloseService.reopenMovement(branchId, movementId, request == null ? null : request.getNote()));
    }

    // ---- history -----------------------------------------------------------

    /** Decisions on the close itself, including every reviewer comment. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<CloseDecisionResponse>> getHistory(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getHistory(branchId, id));
    }

    /** Decisions on every ledger line, each carrying the amount it endorsed. */
    @GetMapping("/{id}/movement-history")
    public ResponseEntity<List<MovementDecisionResponse>> getMovementHistory(@RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID id) {
        return ResponseEntity.ok(cashCloseService.getMovementHistory(branchId, id));
    }
}
