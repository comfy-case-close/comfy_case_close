package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.AttachFileRequest;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.request.SubmitCashCloseRequest;
import com.fnbx.cashclose.dto.request.CorrectCashCloseRequest;
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
import com.fnbx.shared.utils.PagedResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Cash close business logic.
 *
 * <p>Returns DTOs rather than entities so the controller never touches an entity.
 * That is possible without breaking layering because {@code dto} is its own
 * package, not part of {@code controller} - the dependency runs
 * controller to service to dto, never upward.
 *
 * <h2>Relationship with the database layer</h2>
 * Every rule here has a counterpart in the database. That is not redundancy, it is
 * two different jobs: Java fails <b>early and legibly</b> for the user, PostgreSQL
 * makes it <b>impossible</b> even when Java is wrong.
 */
public interface CashCloseService {

    List<ShiftTypeResponse> listShiftTypes(UUID branchId);

    List<DenominationResponse> listDenominations(UUID branchId);

    DaySummaryResponse getDaySummary(UUID branchId, UUID cashCloseId);

    List<CloseAttachmentResponse> getAttachments(UUID branchId, UUID cashCloseId);

    CloseAttachmentResponse attachFile(UUID branchId, UUID cashCloseId,
            AttachFileRequest request);

    // ---- lifecycle ---------------------------------------------------------

    /** Creates and submits a complete close in one transaction. */
    CashCloseResponse submit(UUID branchId, SubmitCashCloseRequest request);

    PagedResponse<CashCloseResponse> listCashCloses(UUID branchId, CashCloseListFilter filter, Pageable pageable);

    CashCloseResponse getById(UUID branchId, UUID cashCloseId);

    /**
     * SUBMITTED or PENDING_REVIEW to APPROVED.
     *
     * <p>Refused while any ledger line is still pending: approving the close with
     * an expense unreviewed means the "explained" figure the manager saw had not
     * finished telling the story.
     */
    CashCloseResponse approve(UUID branchId, UUID cashCloseId, String reviewNote);

    CashCloseResponse reject(UUID branchId, UUID cashCloseId, String reason);

    /** Reviewer's audited correction to a submitted or previously reviewed close. */
    CashCloseResponse correctCashClose(UUID branchId, UUID cashCloseId, CorrectCashCloseRequest request);

    /**
     * Retires a close without deleting it. ADMIN at the close's own branch.
     *
     * <p>VOIDED is terminal and the row stays: a close that was opened by mistake,
     * or duplicated, is itself a fact about the shift. The unique index
     * {@code uq_close_live} excludes VOIDED rows, which is what lets the day be
     * redone after a void.
     */
    CashCloseResponse voidClose(UUID branchId, UUID cashCloseId, String reason);

    // ---- denomination count ------------------------------------------------

    /** The close's counted notes and coins, with the total the view derives from them. */
    DenominationSetResponse getDenominations(UUID branchId, UUID cashCloseId);

    // ---- catalogue ---------------------------------------------------------

    /**
     * The movement kinds in force on a business date, for the entry dropdown.
     *
     * <p>Takes the date rather than using {@code now()} for the same reason
     * {@link #addMovement} does: a close being entered late must offer the
     * vocabulary of the day it belongs to, not today's.
     */
    List<MovementKindResponse> listMovementKinds(UUID branchId, LocalDate businessDate);

    // ---- cash ledger -------------------------------------------------------

    PagedResponse<CashMovementResponse> listMovements(UUID branchId, CashMovementListFilter filter, Pageable pageable);

    List<CashMovementResponse> getMovements(UUID branchId, UUID cashCloseId);

    /**
     * Adds a ledger line. The request carries a positive amount; the sign comes
     * from the movement kind, which is resolved against the close's BUSINESS DATE
     * so a late submission still follows that day's rules.
     */
    CashMovementResponse addMovement(UUID branchId, UUID cashCloseId, AddMovementRequest request);

    /** Audits a correction and returns the line to PENDING while its close is editable. */
    CashMovementResponse updateMovement(UUID branchId, UUID movementId, UpdateMovementRequest request);

    CashMovementResponse approveMovement(UUID branchId, UUID movementId, String note);

    /** Requires a reason: a silent rejection makes a dispute unresolvable. */
    CashMovementResponse rejectMovement(UUID branchId, UUID movementId, String reason);

    // ---- history -----------------------------------------------------------

    /** Decisions on the close itself, including every reviewer comment. */
    List<CloseDecisionResponse> getHistory(UUID branchId, UUID cashCloseId);

    /**
     * Decisions on every ledger line of this close, oldest first. Each entry
     * carries the amount it endorsed, so an amount changed after approval shows up.
     */
    List<MovementDecisionResponse> getMovementHistory(UUID branchId, UUID cashCloseId);
}
