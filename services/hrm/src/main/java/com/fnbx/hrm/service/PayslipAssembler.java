package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.repository.PayslipEmailLogRepository;
import com.fnbx.identity.entity.Staff;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the payslip summary shown to HR and to the employee, so both views stay identical. */
@Component
@RequiredArgsConstructor
public class PayslipAssembler {

    private static final String NOT_SENT = "NOT_SENT";

    private final EntityManager entityManager;
    private final PayslipEmailLogRepository emailLogRepository;

    public PayslipResponse toResponse(Payslip payslip) {
        Staff staff = entityManager.find(Staff.class, payslip.getStaffId());
        String latestEmailStatus = emailLogRepository.findByPayslipIdOrderBySentAtDesc(payslip.getPayslipId()).stream()
                .findFirst().map(log -> log.getStatus().name()).orElse(NOT_SENT);

        return PayslipResponse.builder()
                .payslipId(payslip.getPayslipId())
                .periodId(payslip.getPeriodId())
                .staffId(payslip.getStaffId())
                .employeeCode(staff == null ? null : staff.getEmployeeCode())
                .employeeName(staff == null ? null : staff.getFirstName() + " " + staff.getLastName())
                .payrollLineCount(payslip.getPayrollLineCount())
                .grossTotal(payslip.getGrossTotal())
                .employeeInsuranceTotal(payslip.getEmployeeInsuranceTotal())
                .advanceTotal(payslip.getAdvanceTotal())
                .deductionTotal(payslip.getDeductionTotal())
                .netTotal(payslip.getNetTotal())
                .lateDayTotal(payslip.getLateDayTotal())
                .paidLeaveDaysUsed(payslip.getPaidLeaveDaysUsed())
                .unpaidLeaveDays(payslip.getUnpaidLeaveDays())
                .annualLeaveRemaining(payslip.getAnnualLeaveRemaining())
                .generatedAt(payslip.getGeneratedAt())
                .emailStatus(latestEmailStatus)
                .build();
    }
}
