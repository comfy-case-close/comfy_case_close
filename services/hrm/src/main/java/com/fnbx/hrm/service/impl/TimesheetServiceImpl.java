package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.BulkTimesheetUpdateRequest;
import com.fnbx.hrm.dto.request.AttendanceInputPreviewRequest;
import com.fnbx.hrm.dto.request.TimesheetCellUpdateRequest;
import com.fnbx.hrm.dto.response.BulkTimesheetUpdateResponse;
import com.fnbx.hrm.dto.response.PayrollLineHourTotalsResponse;
import com.fnbx.hrm.dto.response.AttendanceInputPreviewResponse;
import com.fnbx.hrm.dto.response.PayrollLineResponse;
import com.fnbx.hrm.dto.response.TimesheetCellResponse;
import com.fnbx.hrm.dto.response.TimesheetDayResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import com.fnbx.hrm.dto.response.TimesheetRowResponse;
import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.TimesheetEntry;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AttendanceCodeRepository;
import com.fnbx.hrm.repository.LatePenaltyRuleRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.service.TimesheetService;
import com.fnbx.hrm.service.timesheet.TimesheetCellWriter;
import com.fnbx.hrm.service.timesheet.CellOrigin;
import com.fnbx.hrm.service.timesheet.CellLookups;
import com.fnbx.hrm.service.engine.AttendanceParser;
import com.fnbx.hrm.service.engine.HourAggregator;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.Permission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimesheetServiceImpl implements TimesheetService {

    private static final String INVALID_MESSAGE =
            "Not a valid code. Use a number of hours, CP, KL, or T1-/T2-/T3- followed by hours.";

    private final PayrollPeriodRepository periodRepository;
    private final PayrollLineRepository lineRepository;
    private final TimesheetEntryRepository entryRepository;
    private final AttendanceCodeRepository attendanceCodeRepository;
    private final LatePenaltyRuleRepository lateRuleRepository;
    private final PayrollConfigRepository configRepository;
    private final AttendanceParser parser;
    private final TimesheetCellWriter cellWriter;
    private final HourAggregator aggregator;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public TimesheetGridResponse getGrid(UUID periodId, EmploymentType employmentType, UUID branchId) {
        access.requireBusinessOrBranch(branchId, Permission.PAYROLL_PROCESS, Permission.TIMESHEET_READ);
        PayrollPeriod period = requirePeriod(periodId);
        CellLookups lookups = cellWriter.lookups(period);

        List<PayrollLineResponse> lines = lineRepository.findLineResponses(periodId, employmentType, branchId);
        List<TimesheetRowResponse> rows = lines.stream().map(line -> toRow(line, lookups)).toList();

        List<TimesheetDayResponse> days = period.getStartDate().datesUntil(period.getEndDate().plusDays(1))
                .map(date -> TimesheetDayResponse.builder()
                        .date(date)
                        .isoDow(date.getDayOfWeek().getValue())
                        .weekend(date.getDayOfWeek().getValue() >= 6)
                        .build())
                .toList();

        return TimesheetGridResponse.builder()
                .periodId(periodId)
                .employmentType(employmentType == null ? null : employmentType.name())
                .days(days)
                .rows(rows)
                .build();
    }

    private TimesheetRowResponse toRow(PayrollLineResponse line, CellLookups lookups) {
        List<TimesheetEntry> entries = entryRepository.findByPayrollLineIdIn(List.of(line.getPayrollLineId()));
        List<TimesheetCellResponse> cells = entries.stream()
                .sorted((a, b) -> a.getWorkDate().compareTo(b.getWorkDate()))
                .map(this::toCellResponse)
                .toList();
        HourAggregator.LineHours hours = aggregator.aggregate(entries, lookups.standardHoursPerDay(), lookups.absenceCodeIds());

        return TimesheetRowResponse.builder()
                .payrollLineId(line.getPayrollLineId())
                .staffId(line.getStaffId())
                .employeeCode(line.getEmployeeCode())
                .employeeName(line.getEmployeeName())
                .branchId(line.getBranchId())
                .version(line.getVersion())
                .cells(cells)
                .totals(toTotals(line.getPayrollLineId(), hours, line.getVersion()))
                .build();
    }

    @Override
    @Transactional
    public BulkTimesheetUpdateResponse saveCells(UUID periodId, BulkTimesheetUpdateRequest request) {
        PayrollPeriod period = requireDraftPeriod(periodId);
        CellLookups lookups = cellWriter.lookups(period);

        Map<UUID, PayrollLine> linesById = new LinkedHashMap<>();
        for (TimesheetCellUpdateRequest cell : request.getCells()) {
            PayrollLine line = linesById.computeIfAbsent(cell.getLineId(), this::requireLine);
            requireEditable(line, cell.getExpectedVersion());
        }
        List<TimesheetCellResponse> saved = request.getCells().stream()
                .map(cell -> upsertCell(period, linesById.get(cell.getLineId()), cell.getDate(), cell.getRaw(), lookups, cell.getAdjustReason()))
                .toList();
        linesById.values().forEach(this::bumpVersion);
        markModified(period);

        List<PayrollLineHourTotalsResponse> rowTotals = linesById.keySet().stream()
                .map(lineId -> computeTotals(lineId, lookups))
                .toList();

        return BulkTimesheetUpdateResponse.builder()
                .saved(saved)
                .rowTotals(rowTotals)
                .calculationStale(true)
                .build();
    }

    @Override
    @Transactional
    public TimesheetCellResponse saveCell(UUID lineId, LocalDate date, String rawValue, long expectedVersion,
            String adjustReason) {
        PayrollLine line = requireLine(lineId);
        PayrollPeriod period = requireDraftPeriod(line.getPeriodId());
        requireEditable(line, expectedVersion);
        TimesheetCellResponse saved = upsertCell(period, line, date, rawValue, cellWriter.lookups(period), adjustReason);
        bumpVersion(line);
        markModified(period);
        return saved;
    }

    @Override
    @Transactional
    public void deleteCell(UUID lineId, LocalDate date, String reason) {
        PayrollLine line = requireLine(lineId);
        PayrollPeriod period = requireDraftPeriod(line.getPeriodId());
        access.requireBusinessOrBranch(line.getBranchId(), Permission.PAYROLL_PROCESS, Permission.TIMESHEET_EDIT);
        cellWriter.delete(line, date, reason);
        bumpVersion(line);
        markModified(period);
    }

    private PayrollLine requireLine(UUID lineId) {
        return lineRepository.findById(lineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
    }

    private void requireEditable(PayrollLine line, long expectedVersion) {
        access.requireBusinessOrBranch(line.getBranchId(), Permission.PAYROLL_PROCESS, Permission.TIMESHEET_EDIT);
        if (line.getVersion() != expectedVersion) {
            throw PayrollExceptions.versionConflict();
        }
    }

    private void bumpVersion(PayrollLine line) {
        line.setVersion(line.getVersion() + 1);
        lineRepository.save(line);
    }

    private void markModified(PayrollPeriod period) {
        period.setTimesheetModifiedAt(Instant.now());
        periodRepository.save(period);
    }

    private TimesheetCellResponse upsertCell(PayrollPeriod period, PayrollLine line, LocalDate date, String rawValue,
            CellLookups lookups, String adjustReason) {
        cellWriter.upsert(period, line, date, rawValue, lookups, CellOrigin.manual(adjustReason));
        return entryRepository.findByPayrollLineIdAndWorkDate(line.getPayrollLineId(), date)
                .map(this::toCellResponse)
                .orElseGet(() -> TimesheetCellResponse.builder().workDate(date).rawValue(null)
                        .paidHours(java.math.BigDecimal.ZERO).allowanceHours(java.math.BigDecimal.ZERO).build());
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceInputPreviewResponse previewAttendanceInput(AttendanceInputPreviewRequest request) {
        PayrollConfig config = configRepository.findCurrent(LocalDate.now()).orElseThrow(PayrollExceptions::resourceNotFound);
        Map<String, AttendanceCode> codes = attendanceCodeRepository.findAll().stream()
                .collect(Collectors.toMap(AttendanceCode::getCode, Function.identity()));
        Map<String, LatePenaltyRule> rules = lateRuleRepository.findAllEffective(LocalDate.now()).stream()
                .collect(Collectors.toMap(LatePenaltyRule::getRuleCode, Function.identity()));

        AttendanceParser.Result result = parser.parse(request.getRawValue(), request.getEmploymentType(),
                config.getStandardHoursPerDay(), codes, rules);
        return AttendanceInputPreviewResponse.builder()
                .paidHours(result.paidHours())
                .allowanceHours(result.allowanceHours())
                .invalid(result.invalid())
                .message(result.invalid() ? INVALID_MESSAGE : null)
                .build();
    }

    private PayrollLineHourTotalsResponse computeTotals(UUID lineId, CellLookups lookups) {
        PayrollLine line = lineRepository.findById(lineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
        List<TimesheetEntry> entries = entryRepository.findByPayrollLineIdIn(List.of(lineId));
        HourAggregator.LineHours hours = aggregator.aggregate(entries, lookups.standardHoursPerDay(), lookups.absenceCodeIds());
        return toTotals(lineId, hours, line.getVersion());
    }

    private PayrollLineHourTotalsResponse toTotals(UUID lineId, HourAggregator.LineHours hours, long version) {
        return PayrollLineHourTotalsResponse.builder()
                .payrollLineId(lineId)
                .totalHours(hours.totalHours())
                .standardHours(hours.standardHours())
                .overtimeHours(hours.overtimeHours())
                .weekendHours(hours.weekendHours())
                .standardWorkdays(hours.standardWorkdays())
                .lateDayCount(hours.lateDayCount())
                .lateShiftCount(hours.lateShiftCount())
                .absenceDayCount(hours.absenceDayCount())
                .hasInvalidCode(hours.hasInvalidCode())
                .version(version)
                .build();
    }

    private TimesheetCellResponse toCellResponse(TimesheetEntry entry) {
        return TimesheetCellResponse.builder()
                .workDate(entry.getWorkDate())
                .rawValue(entry.getRawValue())
                .paidHours(entry.getPaidHours())
                .allowanceHours(entry.getAllowanceHours())
                .invalid(entry.isInvalid())
                .message(entry.isInvalid() ? INVALID_MESSAGE : null)
                .build();
    }

    private PayrollPeriod requirePeriod(UUID periodId) {
        return periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
    }

    private PayrollPeriod requireDraftPeriod(UUID periodId) {
        PayrollPeriod period = requirePeriod(periodId);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("The timesheet can only be edited while the period is a draft");
        }
        return period;
    }
}
