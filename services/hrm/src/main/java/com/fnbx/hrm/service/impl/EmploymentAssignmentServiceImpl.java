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
        if (payrollLineRepository.usedByNonDraftPeriod(assignmentId)) {
            throw PayrollExceptions.periodLocked(
                    "This assignment is used by a non-draft payroll period - end it and create a new one instead");
        }
        return mapper.toResponse(writer.update(assignment, request));
    }

    @Override
    @Transactional
    public EmploymentAssignmentResponse end(UUID assignmentId, EndEmploymentAssignmentRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        EmploymentAssignment assignment = requireAssignment(assignmentId);
        assignment.setEffectiveTo(request.getEffectiveTo());
        return mapper.toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    private EmploymentAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId).orElseThrow(PayrollExceptions::assignmentNotFound);
    }
}
