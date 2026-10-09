package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.config.HrmContractProperties;
import com.fnbx.hrm.dto.request.CreateEmployeeRequest;
import com.fnbx.hrm.dto.request.EmployeeListFilter;
import com.fnbx.hrm.dto.request.EmployeeProfileInput;
import com.fnbx.hrm.dto.request.TerminateEmployeeRequest;
import com.fnbx.hrm.dto.request.UpdateEmployeeRequest;
import com.fnbx.hrm.dto.response.EmployeeListRow;
import com.fnbx.hrm.dto.response.EmployeeOverviewResponse;
import com.fnbx.hrm.dto.response.EmployeeResponse;
import com.fnbx.hrm.dto.response.EmployeeRestrictedResponse;
import com.fnbx.hrm.dto.response.EmployeeSummaryResponse;
import com.fnbx.hrm.dto.response.PayrollHistoryEntryResponse;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.EmployeeMapper;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.hrm.service.EmployeeService;
import com.fnbx.hrm.service.employee.EmployeeProfileApplier;
import com.fnbx.hrm.service.employee.EmployeeSummaryAssembler;
import com.fnbx.hrm.service.employee.ProfileCompletenessCalculator;
import com.fnbx.identity.entity.Staff;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeProfileRepository employeeProfileRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final EmployeeProfileApplier profileApplier;
    private final EmployeeSummaryAssembler summaryAssembler;
    private final ProfileCompletenessCalculator completenessCalculator;
    private final HrmContractProperties contractProperties;
    private final EmployeeMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EmployeeSummaryResponse> listEmployees(EmployeeListFilter filter, Pageable pageable) {
        access.requireAnyBusiness(Permission.HR_RECORD_READ, Permission.PAYROLL_PROCESS);
        String search = filter.getSearch() == null ? null : "%" + filter.getSearch().toLowerCase() + "%";
        Page<EmployeeListRow> rows = employeeProfileRepository.search(search, filter.getBranchId(),
                filter.getEmploymentType(), filter.getActive(), pageable);
        List<EmployeeSummaryResponse> summaries = summaryAssembler.assemble(rows.getContent());
        return PaginationUtils.toPagedResponse(
                new org.springframework.data.domain.PageImpl<>(summaries, pageable, rows.getTotalElements()), summary -> summary);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeOverviewResponse overview() {
        access.requireAnyBusiness(Permission.HR_RECORD_READ, Permission.PAYROLL_PROCESS);
        LocalDate today = LocalDate.now();
        List<EmployeeProfile> active = employeeProfileRepository.findByTerminatedOnIsNull();
        long incomplete = active.stream().filter(p -> completenessCalculator.calculate(p).percent() < 100).count();
        long expiring = assignmentRepository
                .findEndingBetween(today, today.plusDays(contractProperties.expiryWarningDays())).stream()
                .map(a -> a.getStaffId()).distinct().count();
        return new EmployeeOverviewResponse(active.size(), expiring,
                employeeProfileRepository.countBirthdaysInMonth(today.getMonthValue()), incomplete);
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        Staff staff = requireStaff(request.staffId());
        if (employeeProfileRepository.existsById(staff.getStaffId())) {
            throw PayrollExceptions.resourceConflict("This staff member already has a payroll profile");
        }
        EmployeeProfile profile = new EmployeeProfile();
        profile.setStaffId(staff.getStaffId());
        profile.setBusinessId(TenantContext.current().businessId());
        if (request.profile() != null) {
            profileApplier.apply(profile, request.profile());
        }
        return toResponse(staff, employeeProfileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID staffId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return toResponse(requireStaff(staffId), requireProfile(staffId));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeRestrictedResponse getEmployeeRestricted(UUID branchId, UUID staffId) {
        branchAccess.require(branchId, Permission.TIMESHEET_READ);
        return mapper.toRestrictedResponse(requireStaff(staffId), requireProfile(staffId));
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(UUID staffId, UpdateEmployeeRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        EmployeeProfile profile = requireProfile(staffId);
        if (profile.getVersion() != request.expectedVersion()) {
            throw PayrollExceptions.versionConflict();
        }
        EmployeeProfileInput input = request.profile();
        profileApplier.apply(profile, input);
        profile.setVersion(profile.getVersion() + 1);
        return toResponse(requireStaff(staffId), employeeProfileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public EmployeeResponse terminateEmployee(UUID staffId, TerminateEmployeeRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        EmployeeProfile profile = requireProfile(staffId);
        profile.setTerminatedOn(request.terminatedOn());
        profile.setTerminationReason(request.reasonCode());
        if (request.note() != null && !request.note().isBlank()) {
            profile.setNote(appendNote(profile.getNote(), request.note()));
        }
        return toResponse(requireStaff(staffId), employeeProfileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollHistoryEntryResponse> getPayrollHistory(UUID staffId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return payrollLineRepository.findPayrollHistory(staffId);
    }

    private EmployeeResponse toResponse(Staff staff, EmployeeProfile profile) {
        return mapper.toResponse(staff, profile, completenessCalculator.calculate(profile));
    }

    private String appendNote(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + "\n" + addition;
    }

    private Staff requireStaff(UUID staffId) {
        Staff staff = entityManager.find(Staff.class, staffId);
        if (staff == null) {
            throw PayrollExceptions.employeeNotFound();
        }
        return staff;
    }

    private EmployeeProfile requireProfile(UUID staffId) {
        return employeeProfileRepository.findById(staffId).orElseThrow(PayrollExceptions::employeeNotFound);
    }
}
