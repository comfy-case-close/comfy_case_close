package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.response.CashCloseResponse;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.CloseDecisionResponse;
import com.fnbx.cashclose.dto.response.MovementDecisionResponse;
import com.fnbx.shared.utils.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

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

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Opens a new close. Expected revenue is taken from the POS when available;
     * MANUAL is still allowed when the POS is down, but flagged and alerted on.
     */
    CashCloseResponse openDraft(OpenDraftRequest request);

    PagedResponse<CashCloseResponse> listCashCloses(CashCloseListFilter filter, Pageable pageable);

    CashCloseResponse getById(UUID cashCloseId);

    /** DRAFT to SUBMITTED. Requires a denomination count. */
    CashCloseResponse submit(UUID cashCloseId, String note);

    /**
     * SUBMITTED or PENDING_REVIEW to APPROVED.
     *
     * <p>Refused while any ledger line is still pending: approving the close with
     * an expense unreviewed means the "explained" figure the manager saw had not
     * finished telling the story.
     */
    CashCloseResponse approve(UUID cashCloseId, String reviewNote);

    CashCloseResponse reject(UUID cashCloseId, String reason);

    /** Sends a rejected or review-pending close back to DRAFT so staff can fix it. */
    CashCloseResponse reopen(UUID cashCloseId, String reason);

    // ---- cash ledger -------------------------------------------------------

    PagedResponse<CashMovementResponse> listMovements(CashMovementListFilter filter, Pageable pageable);

    List<CashMovementResponse> getMovements(UUID cashCloseId);

    /**
     * Adds a ledger line. The request carries a positive amount; the sign comes
     * from the movement kind, which is resolved against the close's BUSINESS DATE
     * so a late submission still follows that day's rules.
     */
    CashMovementResponse addMovement(UUID cashCloseId, AddMovementRequest request);

    /** Corrects a line. Only allowed while PENDING - see {@link UpdateMovementRequest}. */
    CashMovementResponse updateMovement(UUID movementId, UpdateMovementRequest request);

    CashMovementResponse approveMovement(UUID movementId, String note);

    /** Requires a reason: a silent rejection makes a dispute unresolvable. */
    CashMovementResponse rejectMovement(UUID movementId, String reason);

    /** Sends a decided line back to PENDING so it can be corrected. Audited. */
    CashMovementResponse reopenMovement(UUID movementId, String reason);

    // ---- history -----------------------------------------------------------

    /** Decisions on the close itself, including every reviewer comment. */
    List<CloseDecisionResponse> getHistory(UUID cashCloseId);

    /**
     * Decisions on every ledger line of this close, oldest first. Each entry
     * carries the amount it endorsed, so an amount changed after approval shows up.
     */
    List<MovementDecisionResponse> getMovementHistory(UUID cashCloseId);
}
