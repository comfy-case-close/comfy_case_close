package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.dto.response.TimesheetCellResponse;
import com.fnbx.hrm.dto.response.TimesheetDayResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import com.fnbx.hrm.dto.response.TimesheetRowResponse;
import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.TimesheetEntry;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AttendanceCodeRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.service.AnnualLeaveBalanceCalculator;
import com.fnbx.hrm.service.PayrollService;
import com.fnbx.hrm.service.PayslipAssembler;
import com.fnbx.hrm.service.engine.HourAggregator;
import com.fnbx.shared.security.AccessPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollLineItemRepository itemRepository;
    private final TimesheetEntryRepository entryRepository;
    private final AttendanceCodeRepository attendanceCodeRepository;
    private final PayrollConfigRepository configRepository;
    private final AnnualLeaveBalanceCalculator balanceCalculator;
    private final HourAggregator aggregator;
    private final PayslipAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public List<PayslipResponse> getOwnPayslips() {
        UUID staffId = currentStaffId();
        return payslipRepository.findByStaffId(staffId).stream()
                .filter(p -> isVisible(p.getPeriodId()))
                .map(assembler::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipDetailResponse getOwnPayslip(UUID payslipId) {
        Payslip payslip = requireOwnPayslip(payslipId);
        return PayslipDetailResponse.builder()
                .payslip(assembler.toResponse(payslip))
                .lines(lineRepository.findLineResponses(payslip.getPeriodId(), null, null).stream()
                        .filter(l -> l.getStaffId().equals(payslip.getStaffId())).toList())
                .components(itemRepository.sumComponentsForEmployee(payslip.getPeriodId(), payslip.getStaffId()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TimesheetGridResponse getOwnTimesheet(UUID periodId) {
        UUID staffId = currentStaffId();
        PayrollPeriod period = periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
        PayrollConfig config = configRepository.findById(period.getConfigId()).orElseThrow(PayrollExceptions::resourceNotFound);
        Set<UUID> absenceCodeIds = attendanceCodeRepository.findAll().stream()
                .filter(AttendanceCode::isCountsAsAbsence).map(AttendanceCode::getAttendanceCodeId)
                .collect(Collectors.toSet());

        var lines = lineRepository.findLineResponses(periodId, null, null).stream()
                .filter(l -> l.getStaffId().equals(staffId)).toList();
        List<TimesheetRowResponse> rows = lines.stream().map(line -> {
            List<TimesheetEntry> entries = entryRepository.findByPayrollLineIdIn(List.of(line.getPayrollLineId()));
            HourAggregator.LineHours hours = aggregator.aggregate(entries, config.getStandardHoursPerDay(), absenceCodeIds);
            List<TimesheetCellResponse> cells = entries.stream()
                    .sorted((a, b) -> a.getWorkDate().compareTo(b.getWorkDate()))
                    .map(e -> TimesheetCellResponse.builder()
                            .workDate(e.getWorkDate()).rawValue(e.getRawValue())
                            .paidHours(e.getPaidHours()).allowanceHours(e.getAllowanceHours())
                            .invalid(e.isInvalid()).build())
                    .toList();
            return TimesheetRowResponse.builder()
                    .payrollLineId(line.getPayrollLineId())
                    .staffId(staffId)
                    .employeeCode(line.getEmployeeCode())
                    .employeeName(line.getEmployeeName())
                    .branchId(line.getBranchId())
                    .version(line.getVersion())
                    .cells(cells)
                    .totals(com.fnbx.hrm.dto.response.PayrollLineHourTotalsResponse.builder()
                            .payrollLineId(line.getPayrollLineId())
                            .totalHours(hours.totalHours()).standardHours(hours.standardHours())
                            .overtimeHours(hours.overtimeHours()).weekendHours(hours.weekendHours())
                            .standardWorkdays(hours.standardWorkdays())
                            .lateDayCount(hours.lateDayCount()).absenceDayCount(hours.absenceDayCount())
                            .hasInvalidCode(hours.hasInvalidCode()).version(line.getVersion())
                            .build())
                    .build();
        }).toList();

        List<TimesheetDayResponse> days = period.getStartDate().datesUntil(period.getEndDate().plusDays(1))
                .map(date -> TimesheetDayResponse.builder().date(date).isoDow(date.getDayOfWeek().getValue())
                        .weekend(date.getDayOfWeek().getValue() >= 6).build())
                .toList();

        return TimesheetGridResponse.builder().periodId(periodId).days(days).rows(rows).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualLeaveBalanceResponse getOwnAnnualLeaveBalance(short year) {
        return balanceCalculator.calculate(currentStaffId(), year);
    }

    private boolean isVisible(UUID periodId) {
        return periodRepository.findById(periodId)
                .map(p -> p.getStatus() != com.fnbx.hrm.enums.PeriodStatus.DRAFT)
                .orElse(false);
    }

    private Payslip requireOwnPayslip(UUID payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(PayrollExceptions::payslipNotFound);
        if (!payslip.getStaffId().equals(currentStaffId()) || !isVisible(payslip.getPeriodId())) {
            throw PayrollExceptions.payslipNotFound();
        }
        return payslip;
    }

    private UUID currentStaffId() {
        return AccessPrincipal.current().staffId();
    }

}
