package com.fnbx.identity.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fnbx.identity.dto.request.ApproveJoinRequest;
import com.fnbx.identity.dto.request.RejectJoinRequest;
import com.fnbx.identity.dto.response.JoinRequestResponse;
import com.fnbx.identity.dto.response.StaffResponse;
import com.fnbx.identity.entity.BranchProfile;
import com.fnbx.identity.entity.JoinRequest;
import com.fnbx.identity.entity.StaffProfile;
import com.fnbx.identity.enums.JoinRequestStatus;
import com.fnbx.identity.exception.AuthExceptions;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.BranchRepository;
import com.fnbx.identity.repository.StaffBranchRoleRepository;
import com.fnbx.identity.repository.StaffJoinRequestRepository;
import com.fnbx.identity.repository.StaffRepository;
import com.fnbx.identity.security.StaffPasswordEncoder;
import com.fnbx.identity.service.JoinRequestService;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.enums.UserRole;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Implementation of {@link JoinRequestService}. */
@Service
public class JoinRequestServiceImpl implements JoinRequestService {

    private final StaffJoinRequestRepository joinRequests;
    private final StaffRepository staff;
    private final BranchRepository branches;
    private final StaffBranchRoleRepository assignments;
    private final StaffPasswordEncoder passwords;
    private final TenantTransactions transactions;

    public JoinRequestServiceImpl(StaffJoinRequestRepository joinRequests, StaffRepository staff,
            BranchRepository branches, StaffBranchRoleRepository assignments, StaffPasswordEncoder passwords,
            TenantTransactions transactions) {
        this.joinRequests = joinRequests; this.staff = staff; this.branches = branches;
        this.assignments = assignments; this.passwords = passwords; this.transactions = transactions;
    }

    @Override
    public PagedResponse<JoinRequestResponse> list(JoinRequestStatus status, int page, int size) {
        AccessPrincipal.current().requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        return transactions.inCurrentTenant(() -> {
            long total = joinRequests.count(status);
            List<JoinRequest> rows = joinRequests.page(status, size, (long) page * size);
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), total), JoinRequestServiceImpl::response);
        });
    }

    @Override
    public JoinRequestResponse get(UUID joinRequestId) {
        AccessPrincipal.current().requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        return transactions.inCurrentTenant(() -> response(
                joinRequests.find(joinRequestId).orElseThrow(OnboardingExceptions::joinRequestNotFound)));
    }

    @Override
    public StaffResponse approve(UUID joinRequestId, ApproveJoinRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        if (request.role() == UserRole.ADMIN && !caller.hasAnyBranch(UserRole.ADMIN)) {
            throw OnboardingExceptions.adminGrantRequiresAdmin();
        }
        return transactions.inCurrentTenant(() -> {
            // Locked, so two reviewers clicking approve at once produce one account.
            JoinRequest application = joinRequests.lockById(joinRequestId)
                    .orElseThrow(OnboardingExceptions::joinRequestNotFound);
            if (application.status() != JoinRequestStatus.PENDING) {
                throw OnboardingExceptions.joinRequestAlreadyDecided();
            }
            BranchProfile branch = branches.find(request.branchId())
                    .orElseThrow(OnboardingExceptions::branchNotFound);
            if (!branch.active()) throw OnboardingExceptions.branchNotFound();
            // Somebody may have been provisioned by hand while this sat in the queue.
            if (staff.emailExists(application.email())) throw AuthExceptions.emailAlreadyExists();

            // Verified on creation: the OTP proved this address when the request was filed.
            // A Google application carries no password, so it gets a hash nobody holds the
            // input to - the account signs in through Google, never locally.
            String hash = application.passcodeHash() == null
                    ? passwords.encode(UUID.randomUUID().toString()) : application.passcodeHash();
            UUID staffId = staff.create(caller.businessId(), application.email(), application.firstName(),
                    application.lastName(), application.phone(), hash, application.authProvider(),
                    application.avatarUrl(), true);
            assignments.assign(staffId, branch.branchId(), caller.businessId(), request.role());
            if (!joinRequests.approve(joinRequestId, caller.staffId(), staffId, trimmed(request.note()))) {
                throw OnboardingExceptions.joinRequestAlreadyDecided();
            }
            return staffResponse(staff.find(staffId).orElseThrow(OnboardingExceptions::staffNotFound));
        });
    }

    @Override
    public JoinRequestResponse reject(UUID joinRequestId, RejectJoinRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN, UserRole.HR);
        return transactions.inCurrentTenant(() -> {
            JoinRequest application = joinRequests.lockById(joinRequestId)
                    .orElseThrow(OnboardingExceptions::joinRequestNotFound);
            if (application.status() != JoinRequestStatus.PENDING) {
                throw OnboardingExceptions.joinRequestAlreadyDecided();
            }
            joinRequests.reject(joinRequestId, caller.staffId(), request.reason().strip());
            return response(joinRequests.find(joinRequestId).orElseThrow(OnboardingExceptions::joinRequestNotFound));
        });
    }

    // ------------------------------------------------------------------------

    private StaffResponse staffResponse(StaffProfile profile) {
        return StaffResponse.builder()
                .id(profile.staffId()).businessId(profile.businessId()).employeeCode(profile.employeeCode())
                .firstName(profile.firstName()).lastName(profile.lastName()).email(profile.email())
                .phone(profile.phone()).avatarUrl(profile.avatarUrl()).active(profile.active())
                .emailVerified(profile.emailVerified())
                .branchRoles(Map.copyOf(assignments.rolesFor(profile.staffId())))
                .build();
    }

    /** Note what is absent: the password hash the row carries while it is still pending. */
    private static JoinRequestResponse response(JoinRequest application) {
        return JoinRequestResponse.builder()
                .joinRequestId(application.joinRequestId()).email(application.email())
                .firstName(application.firstName()).lastName(application.lastName()).phone(application.phone())
                .authProvider(application.authProvider()).status(application.status())
                .requestedAt(application.requestedAt()).decidedBy(application.decidedBy())
                .decidedAt(application.decidedAt()).decisionNote(application.decisionNote())
                .createdStaffId(application.createdStaffId())
                .build();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
