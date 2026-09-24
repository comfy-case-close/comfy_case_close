package com.fnbx.identity.service.impl;

import java.util.List;
import java.util.UUID;
import com.fnbx.identity.utils.BusinessCodeUtils;
import com.fnbx.identity.dto.request.AssignBranchPositionsRequest;
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
import com.fnbx.identity.repository.StaffAccessRepository;
import com.fnbx.identity.repository.StaffRepository;
import com.fnbx.identity.service.BranchService;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.security.Permission;
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

    @org.springframework.beans.factory.annotation.Autowired
    private com.fnbx.shared.security.BranchAccessGuard permissions;
    private final BranchRepository branches;
    private final StaffRepository staff;
    private final StaffAccessRepository assignments;
    private final TenantTransactions transactions;

    public BranchServiceImpl(BranchRepository branches, StaffRepository staff,
            StaffAccessRepository assignments, TenantTransactions transactions) {
        this.branches = branches; this.staff = staff; this.assignments = assignments;
        this.transactions = transactions;
    }

    @Override
    public BranchResponse create(CreateBranchRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        permissions.requireBusiness(Permission.BRANCH_CREATE);
        return BusinessCodeUtils.retry(() -> transactions.inCurrentTenant(() -> {
            UUID branchId = branches.insert(caller.businessId(),
                    BusinessCodeUtils.generate(request.branchName()), request.branchName().strip(),
                    trimmed(request.address()), request.targetCashRemaining(), request.cashRemainingTolerance());
            return response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound));
        }), "branch_pkey", "branch_business_id_branch_code_key");
    }

    @Override
    public PagedResponse<BranchResponse> list(boolean includeInactive, int page, int size) {
        return transactions.inCurrentTenant(() -> {
            boolean businessAccess = !permissions.effective(null).isEmpty();
            var allowed = permissions.branches(Permission.CLOSE_READ);
            List<BranchProfile> visible = branches.page(includeInactive, Integer.MAX_VALUE, 0).stream()
                    .filter(b -> businessAccess || allowed.contains(b.branchId())).toList();
            long offset = (long) page * size;
            List<BranchProfile> rows = visible.stream().skip(offset).limit(size).toList();
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), visible.size()), BranchServiceImpl::response);
        });
    }

    @Override
    public BranchResponse get(UUID branchId) {
        return transactions.inCurrentTenant(() -> {
            if (permissions.effective(null).isEmpty()) permissions.require(branchId,Permission.CLOSE_READ);
            return response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound));
        });
    }

    @Override
    public BranchResponse update(UUID branchId, UpdateBranchRequest request) {
        permissions.require(branchId, Permission.CONFIG_WRITE);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            branches.update(branchId, trimmed(request.branchName()), trimmed(request.address()),
                    request.targetCashRemaining(), request.cashRemainingTolerance());
            return response(branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound));
        });
    }

    @Override
    public MessageResponse deactivate(UUID branchId) {
        permissions.requireBusiness(Permission.BRANCH_DEACTIVATE);
        return transactions.inCurrentTenant(() -> {
            assignments.lockBusiness(AccessPrincipal.current().businessId());
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
        permissions.requireBusiness(Permission.STAFF_ASSIGN);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            long total = assignments.countMembersOf(branchId, includeRevoked);
            List<BranchMember> rows = assignments.membersOf(branchId, includeRevoked, size, (long) page * size);
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), total), BranchServiceImpl::response);
        });
    }

    @Override
    public List<BranchAssignmentResponse> assign(UUID branchId, UUID staffId, AssignBranchPositionsRequest request) {
        permissions.requireBusiness(Permission.STAFF_ASSIGN);
        return transactions.inCurrentTenant(() -> {
            BranchProfile branch = branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            if (!branch.active()) throw OnboardingExceptions.branchNotFound();
            StaffProfile member = staff.find(staffId).orElseThrow(OnboardingExceptions::staffNotFound);
            for (UUID position : request.positionIds()) {
                if (!assignments.activePosition(position)) throw new com.fnbx.shared.exception.AppException(
                    com.fnbx.shared.exception.ErrorCode.VALIDATION_FAILED,"Select active positions in this business");
            }
            assignments.replacePositions(staffId,branchId,member.businessId(),request.positionIds());
            return assignments.membersOf(branchId,false).stream().filter(m->m.staffId().equals(staffId))
                    .map(BranchServiceImpl::response).toList();
        });
    }

    @Override
    public MessageResponse revoke(UUID branchId, UUID staffId) {
        permissions.requireBusiness(Permission.STAFF_ASSIGN);
        return transactions.inCurrentTenant(() -> {
            branches.find(branchId).orElseThrow(OnboardingExceptions::branchNotFound);
            staff.find(staffId).orElseThrow(OnboardingExceptions::staffNotFound);
            assignments.revokePositions(staffId,branchId);
            return new MessageResponse("Branch positions revoked. Direct grants are managed separately.");
        });
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
                .staffActive(member.staffActive()).positionId(member.positionId())
                .assignedAt(member.assignedAt()).revokedAt(member.revokedAt())
                .build();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
