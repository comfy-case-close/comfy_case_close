package com.fnbx.identity.service.impl;

import java.util.List;
import java.util.UUID;
import com.fnbx.identity.utils.BusinessCodeUtils;
import com.fnbx.identity.dto.request.AssignBranchRoleRequest;
import com.fnbx.identity.dto.request.CreateBranchRequest;
import com.fnbx.identity.dto.request.UpdateBranchRequest;
import com.fnbx.identity.dto.response.BranchAssignmentResponse;
import com.fnbx.identity.dto.response.BranchResponse;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.entity.BranchMember;
import com.fnbx.identity.entity.BranchProfile;
import com.fnbx.identity.entity.StaffProfile;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.BranchRepository;
import com.fnbx.identity.repository.StaffBranchRoleRepository;
import com.fnbx.identity.repository.StaffRepository;
import com.fnbx.identity.service.BranchService;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.enums.UserRole;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link BranchService}.
 *
 * <h2>The two invariants this class exists to protect</h2>
 * A business must keep at least one active branch, and at least one live ADMIN.
 * Either one reaching zero locks every employee out of the tenant with no route back
 * in from inside the product - only a platform administrator could repair it. Both
 * are checked <i>after</i> the write, inside the transaction, so the check sees the
 * world the write created and a violation simply rolls back.
 */
@Service
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branches;
    private final StaffRepository staff;
    private final StaffBranchRoleRepository assignments;
    private final TenantTransactions transactions;

    public BranchServiceImpl(BranchRepository branches, StaffRepository staff,
            StaffBranchRoleRepository assignments, TenantTransactions transactions) {
        this.branches = branches; this.staff = staff; this.assignments = assignments;
        this.transactions = transactions;
    }

    @Override
    public BranchResponse create(CreateBranchRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN);
        return BusinessCodeUtils.retry(() -> transactions.inCurrentTenant(() -> {
            UUID branchId = branches.insert(caller.businessId(),
                    BusinessCodeUtils.generate(request.branchName()), request.branchName().strip(),
                    trimmed(request.address()), request.targetCashRemaining(), request.cashRemainingTolerance());
            return response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound));
        }), "branch_pkey", "branch_business_id_branch_code_key");
    }

    @Override
    public PagedResponse<BranchResponse> list(boolean includeInactive, int page, int size) {
        AccessPrincipal.current().requireAnyBranch();
        return transactions.inCurrentTenant(() -> {
            long total = branches.count(includeInactive);
            List<BranchProfile> rows = branches.page(includeInactive, size, (long) page * size);
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), total), BranchServiceImpl::response);
        });
    }

    @Override
    public BranchResponse get(UUID branchId) {
        AccessPrincipal.current().requireAnyBranch();
        return transactions.inCurrentTenant(
                () -> response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound)));
    }

    @Override
    public BranchResponse update(UUID branchId, UpdateBranchRequest request) {
        AccessPrincipal.current().requireAnyBranch(UserRole.ADMIN);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            branches.update(branchId, trimmed(request.branchName()), trimmed(request.address()),
                    request.targetCashRemaining(), request.cashRemainingTolerance());
            return response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound));
        });
    }

    @Override
    public MessageResponse deactivate(UUID branchId) {
        AccessPrincipal.current().requireAnyBranch(UserRole.ADMIN);
        return transactions.inCurrentTenant(() -> {
            BranchProfile branch = branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            if (!branch.active()) throw OnboardingExceptions.branchNotFound();
            if (branches.countActive() <= 1) throw OnboardingExceptions.lastActiveBranch();
            branches.deactivate(branchId);
            // Grants at this branch are left in place. They stop conferring anything the
            // moment the branch is inactive, and reactivating restores the roster intact.
            return new MessageResponse("Branch deactivated.");
        });
    }

    @Override
    public PagedResponse<BranchAssignmentResponse> members(UUID branchId, boolean includeRevoked, int page, int size) {
        AccessPrincipal.current().requireAnyBranch(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            long total = assignments.countMembersOf(branchId, includeRevoked);
            List<BranchMember> rows = assignments.membersOf(branchId, includeRevoked, size, (long) page * size);
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), total), BranchServiceImpl::response);
        });
    }

    @Override
    public BranchAssignmentResponse assign(UUID branchId, UUID staffId, AssignBranchRoleRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        requireAdminToTouchAdmin(caller, request.role());
        return transactions.inCurrentTenant(() -> {
            BranchProfile branch = branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            if (!branch.active()) throw OnboardingExceptions.branchNotFound();
            StaffProfile member = staff.find(staffId).orElseThrow(OnboardingExceptions::staffNotFound);
            // An HR must not be able to demote the ADMIN who could undo it.
            requireAdminToTouchAdmin(caller, currentRole(branchId, staffId));
            assignments.assign(staffId, branchId, member.businessId(), request.role());
            requireBusinessKeepsAnAdmin();
            return assignments.membersOf(branchId, false).stream()
                    .filter(assignment -> assignment.staffId().equals(staffId)).findFirst()
                    .map(BranchServiceImpl::response)
                    .orElseThrow(OnboardingExceptions::staffNotFound);
        });
    }

    @Override
    public MessageResponse revoke(UUID branchId, UUID staffId) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            staff.find(staffId).orElseThrow(OnboardingExceptions::staffNotFound);
            requireAdminToTouchAdmin(caller, currentRole(branchId, staffId));
            if (!assignments.revoke(staffId, branchId)) {
                // Nothing live to revoke. Idempotent rather than an error: the caller asked
                // for this person to have no access here, and they do not.
                return new MessageResponse("Branch access revoked.");
            }
            requireBusinessKeepsAnAdmin();
            return new MessageResponse("Branch access revoked.");
        });
    }

    // ------------------------------------------------------------------------

    /** Only an ADMIN may hand out, change or take away ADMIN. Null role is not ADMIN. */
    private static void requireAdminToTouchAdmin(AccessPrincipal caller, UserRole role) {
        if (role == UserRole.ADMIN && !caller.hasAnyBranch(UserRole.ADMIN)) {
            throw OnboardingExceptions.adminGrantRequiresAdmin();
        }
    }

    private UserRole currentRole(UUID branchId, UUID staffId) {
        return assignments.membersOf(branchId, false).stream()
                .filter(assignment -> assignment.staffId().equals(staffId))
                .map(BranchMember::role).findFirst().orElse(null);
    }

    private void requireBusinessKeepsAnAdmin() {
        if (!staff.hasLiveAdmin()) throw OnboardingExceptions.lastActiveAdmin();
    }

    private static BranchResponse response(BranchProfile branch) {
        return BranchResponse.builder()
                .branchId(branch.branchId()).businessId(branch.businessId()).branchCode(branch.branchCode())
                .branchName(branch.branchName()).address(branch.address())
                .targetCashRemaining(branch.targetCashRemaining())
                .cashRemainingTolerance(branch.cashRemainingTolerance())
                .active(branch.active()).createdAt(branch.createdAt()).updatedAt(branch.updatedAt())
                .build();
    }

    private static BranchAssignmentResponse response(BranchMember member) {
        return BranchAssignmentResponse.builder()
                .staffId(member.staffId()).branchId(member.branchId()).employeeCode(member.employeeCode())
                .firstName(member.firstName()).lastName(member.lastName()).email(member.email())
                .staffActive(member.staffActive()).role(member.role())
                .assignedAt(member.assignedAt()).revokedAt(member.revokedAt())
                .build();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
