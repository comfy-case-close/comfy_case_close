package com.fnbx.identity.service;

import java.util.UUID;
import com.fnbx.identity.dto.request.AssignBranchRoleRequest;
import com.fnbx.identity.dto.request.CreateBranchRequest;
import com.fnbx.identity.dto.request.UpdateBranchRequest;
import com.fnbx.identity.dto.response.BranchAssignmentResponse;
import com.fnbx.identity.dto.response.BranchResponse;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.shared.utils.PagedResponse;

/**
 * Branch structure and who works where.
 *
 * <p>Two kinds of operation live together here on purpose. Opening a branch and
 * staffing it are the same job - a branch with nobody assigned is as useless as an
 * assignment to a branch that does not exist - and both are answered by the same
 * pair of tables.
 *
 * <p>Every method takes its business from the verified token. None accepts a
 * {@code businessId}: that would let a caller choose which tenant they are.
 */
public interface BranchService {

    /** ADMIN only. Branch codes are unique within the business. */
    BranchResponse create(CreateBranchRequest request);

    /** Any assigned role may see their own business's branches. */
    PagedResponse<BranchResponse> list(boolean includeInactive, int page, int size);

    BranchResponse get(UUID branchId);

    /** ADMIN only. Absent fields are left alone; the code is immutable. */
    BranchResponse update(UUID branchId, UpdateBranchRequest request);

    /**
     * Deactivates a branch. ADMIN only, and refused for the last active one: every
     * live grant is joined against {@code branch.is_active}, so switching off the last
     * branch would strip the whole business of its access at once.
     */
    MessageResponse deactivate(UUID branchId);

    /** The roster, revoked grants included so "who used to work here" is answerable. */
    PagedResponse<BranchAssignmentResponse> members(UUID branchId, boolean includeRevoked, int page, int size);

    /**
     * Assigns or changes one person's role at one branch. ADMIN or HR - but only an
     * ADMIN may grant or withdraw ADMIN, and the business may never be left without a
     * live one.
     */
    BranchAssignmentResponse assign(UUID branchId, UUID staffId, AssignBranchRoleRequest request);

    /** Revokes a live grant, keeping the row. Same authority rules as {@link #assign}. */
    MessageResponse revoke(UUID branchId, UUID staffId);
}
