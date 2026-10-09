package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.service.attendance.AttendancePlanner.PlannedCell;
import com.fnbx.hrm.service.timesheet.CellLookups;
import com.fnbx.hrm.service.timesheet.CellOrigin;
import com.fnbx.hrm.service.timesheet.TimesheetCellWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Writes the cells of a confirmed attendance sheet into the payroll timesheet of the period each day falls in. */
@Component
@RequiredArgsConstructor
public class AttendanceTimesheetWriter {

    private final AttendancePeriods periods;
    private final AttendanceLineResolver lineResolver;
    private final TimesheetCellWriter cellWriter;
    private final PayrollLineRepository lineRepository;
    private final PayrollPeriodRepository periodRepository;

    /** @return how many cells were left alone because HR had already changed them by hand */
    public int write(UUID attendanceSheetId, UUID branchId, List<PlannedCell> cells) {
        Map<UUID, CellLookups> lookups = new HashMap<>();
        Set<PayrollPeriod> touchedPeriods = new HashSet<>();
        Set<PayrollLine> touchedLines = new HashSet<>();
        int skipped = 0;
        for (PlannedCell planned : cells) {
            PayrollPeriod period = periods.requireOpenFor(planned.date());
            PayrollLine line = lineResolver.findOrCreate(period, planned.staffId(), branchId, planned.date());
            CellLookups cellLookups = lookups.computeIfAbsent(period.getPayrollPeriodId(), id -> cellWriter.lookups(period));
            boolean hasValue = planned.cell().rawValue() != null;
            boolean stored = cellWriter.upsert(period, line, planned.date(), planned.cell().rawValue(), cellLookups,
                    CellOrigin.attendance(attendanceSheetId, planned.cell().lateShifts())).isPresent();
            skipped += hasValue && !stored ? 1 : 0;
            touchedPeriods.add(period);
            touchedLines.add(line);
        }
        touchedLines.forEach(line -> line.setVersion(line.getVersion() + 1));
        lineRepository.saveAll(touchedLines);
        touchedPeriods.forEach(period -> period.setTimesheetModifiedAt(Instant.now()));
        periodRepository.saveAll(touchedPeriods);
        return skipped;
    }
}
