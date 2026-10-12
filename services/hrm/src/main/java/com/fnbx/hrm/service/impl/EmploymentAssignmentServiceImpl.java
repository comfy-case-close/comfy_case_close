package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.dto.request.EndEmploymentAssignmentRequest;
import com.fnbx.hrm.dto.response.EmploymentAssignmentResponse;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.EmployeeMapper;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.service.EmploymentAssignmentService;
import com.fnbx.hrm.service.contract.EmploymentAssignmentWriter;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmploymentAssignmentServiceImpl implements EmploymentAssignmentService {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final EmploymentAssignmentWriter writer;
    private final EmployeeMapper mapper;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public EmploymentAssignmentResponse get(UUID assignmentId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return mapper.toResponse(requireAssignment(assignmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmploymentAssignmentResponse> listByEmployee(UUID staffId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return assignmentRepository.findByStaffIdOrderByEffectiveFromDesc(staffId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public EmploymentAssignmentResponse create(UUID staffId, EmploymentAssignmentRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        if (!employeeProfileRepository.existsById(staffId)) {
            throw PayrollExceptions.employeeNotFound();
        }
        return mapper.toResponse(writer.create(staffId, request));
    }

    @Override
    @Transactional
    public EmploymentAssignmentResponse update(UUID assignmentId, EmploymentAssignmentRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        EmploymentAssignment assignment = requireAssignment(assignmentId);
        LocalDate closedThrough = payrollLineRepository.latestNonDraftPeriodEnd(assignmentId);
        if (closedThrough == null || !writer.changesPayrollInputs(assignment, request)) {
            return mapper.toResponse(writer.update(assignment, request));
        }
        return mapper.toResponse(replaceWithNewContract(assignment, request, closedThrough));
    }

    @Override
    @Transactional
    public EmploymentAssignmentResponse end(UUID assignmentId, EndEmploymentAssignmentRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        EmploymentAssignment assignment = requireAssignment(assignmentId);
        assignment.setEffectiveTo(request.getEffectiveTo());
        return mapper.toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    private EmploymentAssignment replaceWithNewContract(EmploymentAssignment current, EmploymentAssignmentRequest request,
            LocalDate closedThrough) {
        LocalDate start = latest(request.getEffectiveFrom(), closedThrough.plusDays(1));
        if (request.getEffectiveTo() != null && request.getEffectiveTo().isBefore(start)) {
            throw PayrollExceptions.invalidField("Payroll is closed through " + closedThrough
                    + ", so the changed contract can only start from " + start + " and cannot end before that");
        }
        endBefore(current, start);
        request.setEffectiveFrom(start);
        return writer.create(current.getStaffId(), request);
    }

    private void endBefore(EmploymentAssignment contract, LocalDate replacementStart) {
        if (contract.getEffectiveTo() == null || !contract.getEffectiveTo().isBefore(replacementStart)) {
            contract.setEffectiveTo(replacementStart.minusDays(1));
            assignmentRepository.saveAndFlush(contract);
        }
    }

    private LocalDate latest(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private EmploymentAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId).orElseThrow(PayrollExceptions::assignmentNotFound);
    }
}
