package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.service.PayrollLineBranchPolicy;
import com.fnbx.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Finds the payroll line an attendance cell belongs to, creating it from the contract in force when the period has none yet. */
@Component
@RequiredArgsConstructor
public class AttendanceLineResolver {

    private final PayrollLineRepository lineRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final PayrollLineBranchPolicy branchPolicy;

    public PayrollLine findOrCreate(PayrollPeriod period, UUID staffId, UUID branchId, LocalDate date) {
        List<PayrollLine> existing = lineRepository.findByPeriodAndStaffAndBranch(period.getPayrollPeriodId(), staffId, branchId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        EmploymentAssignment contract = assignmentRepository.findEffectiveForStaff(List.of(staffId), date).stream()
                .min(Comparator.comparing(EmploymentAssignment::getEffectiveFrom))
                .orElseThrow(() -> PayrollExceptions.attendanceNoPayrollLine("The employee has no contract in force on " + date));
        if (!branchPolicy.isAllowed(contract.getDefaultBranchId(), branchId)) {
            throw PayrollExceptions.attendanceNoPayrollLine("The employee's contract does not allow work at this branch");
        }
        PayrollLine line = new PayrollLine();
        line.setPayrollLineId(UUID.randomUUID());
        line.setBusinessId(TenantContext.current().businessId());
        line.setPeriodId(period.getPayrollPeriodId());
        line.setAssignmentId(contract.getEmploymentAssignmentId());
        line.setBranchId(branchId);
        line.setEmploymentType(contract.getEmploymentType());
        return lineRepository.saveAndFlush(line);
    }
}
