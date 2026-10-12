package com.fnbx.hrm.service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.JobLevel;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmploymentAssignmentWriterTest {

    private final UUID positionId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final ContractPayResolver payResolver = mock(ContractPayResolver.class);
    private final EmploymentAssignmentWriter writer =
            new EmploymentAssignmentWriter(mock(EmploymentAssignmentRepository.class), payResolver, mock(ContractNumberGenerator.class));
    private EmploymentAssignment stored;
    private EmploymentAssignmentRequest request;

    @BeforeEach
    void aMonthlyContractAndTheSameRequest() {
        stored = new EmploymentAssignment();
        stored.setPositionId(positionId);
        stored.setDefaultBranchId(branchId);
        stored.setEmploymentType(EmploymentType.FULLTIME);
        stored.setMonthlyBaseSalary(new BigDecimal("5600000.00"));
        stored.setSupplementAllowance(new BigDecimal("800000"));
        stored.setKpiAllowance(BigDecimal.ZERO);
        stored.setResponsibilityAllowance(new BigDecimal("1000000"));
        stored.setInsured(true);
        stored.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        stored.setEffectiveTo(LocalDate.of(2026, 12, 31));
        stored.setContractKind(ContractKind.FIXED_TERM_FT);

        request = new EmploymentAssignmentRequest();
        request.setPositionId(positionId);
        request.setDefaultBranchId(branchId);
        request.setEmploymentType(EmploymentType.FULLTIME);
        request.setKpiAllowance(BigDecimal.ZERO);
        request.setResponsibilityAllowance(new BigDecimal("1000000.00"));
        request.setInsured(true);
        request.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        request.setEffectiveTo(LocalDate.of(2026, 12, 31));
        request.setContractKind(ContractKind.FIXED_TERM_FT);
        when(payResolver.resolve(request)).thenReturn(new ContractPay(new BigDecimal("5600000"), new BigDecimal("800000.00"), null));
    }

    @Test
    void resendingTheSamePayIsNotAChange() {
        assertThat(writer.changesPayrollInputs(stored, request)).isFalse();
    }

    @Test
    void descriptiveFieldsAreNotPayrollInputs() {
        request.setContractKind(ContractKind.INDEFINITE_FT);
        request.setJobLevel(JobLevel.SENIOR);
        request.setNote("Chuyển sang không xác định thời hạn");

        assertThat(writer.changesPayrollInputs(stored, request)).isFalse();
    }

    @Test
    void aNewSalaryIsAChange() {
        when(payResolver.resolve(request)).thenReturn(new ContractPay(new BigDecimal("6000000"), new BigDecimal("800000"), null));

        assertThat(writer.changesPayrollInputs(stored, request)).isTrue();
    }

    @Test
    void aMovedBranchOrEndDateIsAChange() {
        request.setDefaultBranchId(UUID.randomUUID());
        assertThat(writer.changesPayrollInputs(stored, request)).isTrue();

        request.setDefaultBranchId(branchId);
        request.setEffectiveTo(LocalDate.of(2026, 6, 30));
        assertThat(writer.changesPayrollInputs(stored, request)).isTrue();
    }

    @Test
    void switchingFromMonthlyToHourlyIsAChange() {
        stored.setMonthlyBaseSalary(null);
        when(payResolver.resolve(request)).thenReturn(new ContractPay(new BigDecimal("5600000"), BigDecimal.ZERO, null));

        assertThat(writer.changesPayrollInputs(stored, request)).isTrue();
    }
}
