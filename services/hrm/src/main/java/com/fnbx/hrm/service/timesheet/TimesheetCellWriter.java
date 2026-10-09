package com.fnbx.hrm.service.timesheet;

import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.TimesheetEntry;
import com.fnbx.hrm.enums.TimesheetSource;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AttendanceCodeRepository;
import com.fnbx.hrm.repository.LatePenaltyRuleRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.service.engine.AttendanceParser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The one place a timesheet cell is parsed and stored, whether it was typed by HR or came from a confirmed attendance sheet. */
@Component
@RequiredArgsConstructor
public class TimesheetCellWriter {

    private final TimesheetEntryRepository entryRepository;
    private final AttendanceCodeRepository attendanceCodeRepository;
    private final LatePenaltyRuleRepository lateRuleRepository;
    private final PayrollConfigRepository configRepository;
    private final AttendanceParser parser;

    public CellLookups lookups(PayrollPeriod period) {
        PayrollConfig config = configRepository.findById(period.getConfigId()).orElseThrow(PayrollExceptions::resourceNotFound);
        Map<String, AttendanceCode> codes = attendanceCodeRepository.findAll().stream()
                .collect(Collectors.toMap(AttendanceCode::getCode, Function.identity()));
        Map<String, LatePenaltyRule> rules = lateRuleRepository.findAllEffective(period.getEndDate()).stream()
                .collect(Collectors.toMap(LatePenaltyRule::getRuleCode, Function.identity()));
        Set<UUID> absenceCodeIds = codes.values().stream()
                .filter(AttendanceCode::isCountsAsAbsence)
                .map(AttendanceCode::getAttendanceCodeId)
                .collect(Collectors.toSet());
        return new CellLookups(config.getStandardHoursPerDay(), codes, rules, absenceCodeIds);
    }

    /** Stores the cell; an empty value removes it. Returns empty when an attendance write meets a cell HR edited by hand. */
    public Optional<TimesheetEntry> upsert(PayrollPeriod period, PayrollLine line, LocalDate date, String rawValue,
            CellLookups lookups, CellOrigin origin) {
        if (date.isBefore(period.getStartDate()) || date.isAfter(period.getEndDate())) {
            throw PayrollExceptions.workDateOutsidePeriod();
        }
        Optional<TimesheetEntry> existing = entryRepository.findByPayrollLineIdAndWorkDate(line.getPayrollLineId(), date);
        if (!mayReplace(existing, origin)) {
            return Optional.empty();
        }
        if (rawValue == null || rawValue.isBlank()) {
            entryRepository.deleteByPayrollLineIdAndWorkDate(line.getPayrollLineId(), date);
            return Optional.empty();
        }
        AttendanceParser.Result result = parser.parse(rawValue, line.getEmploymentType(), lookups.standardHoursPerDay(),
                lookups.attendanceCodesByCode(), lookups.lateRulesByCode());
        TimesheetEntry entry = existing.orElseGet(() -> newEntry(line, date));
        entry.setRawValue(rawValue);
        entry.setAttendanceCodeId(result.attendanceCodeId());
        entry.setLateRuleId(result.lateRuleId());
        entry.setDeclaredHours(result.declaredHours());
        entry.setPaidHours(result.paidHours());
        entry.setAllowanceHours(result.allowanceHours());
        entry.setInvalid(result.invalid());
        entry.setLateShifts((short) origin.lateShifts());
        entry.setSource(origin.source());
        entry.setAttendanceSheetId(origin.attendanceSheetId());
        entry.setAdjustReason(origin.adjustReason());
        entry.setUpdatedAt(Instant.now());
        entry.setVersion(entry.getVersion() + 1);
        return Optional.of(entryRepository.saveAndFlush(entry));
    }

    public void delete(PayrollLine line, LocalDate date, String reason) {
        requireReasonForAttendanceCell(entryRepository.findByPayrollLineIdAndWorkDate(line.getPayrollLineId(), date), reason);
        entryRepository.deleteByPayrollLineIdAndWorkDate(line.getPayrollLineId(), date);
    }

    private boolean mayReplace(Optional<TimesheetEntry> existing, CellOrigin origin) {
        if (origin.source() == TimesheetSource.ATTENDANCE) {
            return existing.map(entry -> entry.getSource() == TimesheetSource.ATTENDANCE).orElse(true);
        }
        requireReasonForAttendanceCell(existing, origin.adjustReason());
        return true;
    }

    private void requireReasonForAttendanceCell(Optional<TimesheetEntry> existing, String reason) {
        boolean fromAttendance = existing.map(entry -> entry.getSource() == TimesheetSource.ATTENDANCE).orElse(false);
        if (fromAttendance && (reason == null || reason.isBlank())) {
            throw PayrollExceptions.invalidField("A reason is required to change a cell that came from attendance");
        }
    }

    private TimesheetEntry newEntry(PayrollLine line, LocalDate date) {
        TimesheetEntry created = new TimesheetEntry();
        created.setTimesheetEntryId(UUID.randomUUID());
        created.setBusinessId(line.getBusinessId());
        created.setPayrollLineId(line.getPayrollLineId());
        created.setWorkDate(date);
        return created;
    }
}
