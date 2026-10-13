package com.fnbx.hrm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.mapper.EmployeeMapper;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.service.contract.EmploymentAssignmentWriter;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.security.BranchAccessGuard;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmploymentAssignmentReplacementTest {

    private final UUID contractId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final EmploymentAssignmentRepository assignments = mock(EmploymentAssignmentRepository.class);
    private final PayrollLineRepository payrollLines = mock(PayrollLineRepository.class);
    private final EmploymentAssignmentWriter writer = mock(EmploymentAssignmentWriter.class);
    private final EmploymentAssignmentServiceImpl service = new EmploymentAssignmentServiceImpl(
            assignments, mock(EmployeeProfileRepository.class), payrollLines, writer, mock(EmployeeMapper.class), mock(BranchAccessGuard.class));
    private EmploymentAssignment current;
    private EmploymentAssignmentRequest request;

    @BeforeEach
    void anOpenContractAndAnEditRequest() {
        current = new EmploymentAssignment();
        current.setEmploymentAssignmentId(contractId);
        current.setStaffId(staffId);
        current.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(assignments.findById(contractId)).thenReturn(Optional.of(current));
        request = new EmploymentAssignmentRequest();
        request.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(writer.changesPayrollInputs(current, request)).thenReturn(true);
    }

    @Test
    void aContractNoPeriodHasClosedOnIsEditedInPlace() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(null);

        service.update(contractId, request);

        verify(writer).update(current, request);
        verify(writer, never()).create(any(), any());
    }

    @Test
    void aPayChangeAfterAClosedPeriodEndsTheOldContractAndStartsANewOneAfterThePeriod() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(LocalDate.of(2026, 9, 30));

        service.update(contractId, request);

        assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(request.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(assignments).saveAndFlush(current);
        verify(writer).create(staffId, request);
        verify(writer, never()).update(any(), any());
    }

    @Test
    void aLaterRequestedStartIsKept() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(LocalDate.of(2026, 9, 30));
        request.setEffectiveFrom(LocalDate.of(2026, 11, 15));

        service.update(contractId, request);

        assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(request.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void anOldContractThatAlreadyEndedBeforeTheNewOneKeepsItsEndDate() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(LocalDate.of(2026, 9, 30));
        current.setEffectiveTo(LocalDate.of(2026, 9, 30));

        service.update(contractId, request);

        assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        verify(assignments, never()).saveAndFlush(current);
    }

    @Test
    void aReplacementThatWouldEndBeforeItStartsIsRefused() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(LocalDate.of(2026, 9, 30));
        request.setEffectiveTo(LocalDate.of(2026, 9, 15));

        assertThatThrownBy(() -> service.update(contractId, request)).isInstanceOf(AppException.class);
        verify(writer, never()).create(any(), any());
    }

    @Test
    void descriptiveEditsAfterAClosedPeriodStayInPlace() {
        when(payrollLines.latestNonDraftPeriodEnd(contractId)).thenReturn(LocalDate.of(2026, 9, 30));
        when(writer.changesPayrollInputs(current, request)).thenReturn(false);

        service.update(contractId, request);

        verify(writer).update(current, request);
        verify(writer, never()).create(any(), any());
    }
}
