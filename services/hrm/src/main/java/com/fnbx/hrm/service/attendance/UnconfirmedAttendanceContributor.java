package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.AttendanceSheet;
import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.enums.AttendanceSheetStatus;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.repository.AttendanceSheetRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.service.engine.PeriodIssueContributor;
import com.fnbx.hrm.service.engine.ValidationIssueFactory;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Warns when a finished week inside the period still has attendance that nobody confirmed. */
@Component
@RequiredArgsConstructor
public class UnconfirmedAttendanceContributor implements PeriodIssueContributor {

    private static final int DAYS_IN_WEEK = 7;

    private final ShiftScheduleRepository scheduleRepository;
    private final AttendanceSheetRepository sheetRepository;
    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        LocalDate today = LocalDate.now();
        List<ShiftSchedule> weeks = scheduleRepository
                .findWeeksBetween(period.getStartDate().minusDays(DAYS_IN_WEEK - 1), period.getEndDate()).stream()
                .filter(schedule -> schedule.getStatus() == ScheduleStatus.PUBLISHED && WeekCalendar.hasEnded(schedule.getWeekStart(), today))
                .toList();
        Map<UUID, AttendanceSheet> sheets = sheetRepository.findByShiftScheduleIdIn(weeks.stream().map(ShiftSchedule::getShiftScheduleId).toList())
                .stream().collect(Collectors.toMap(AttendanceSheet::getShiftScheduleId, Function.identity()));
        return weeks.stream()
                .filter(week -> sheets.get(week.getShiftScheduleId()) == null
                        || sheets.get(week.getShiftScheduleId()).getStatus() != AttendanceSheetStatus.CONFIRMED)
                .map(week -> issueFactory.warning(period, IssueCode.ATTENDANCE_NOT_CONFIRMED, "schedule:" + week.getShiftScheduleId(),
                        "Week of " + week.getWeekStart() + " has " + daysInPeriod(week, period) + " day(s) in this period and no confirmed attendance"))
                .toList();
    }

    private long daysInPeriod(ShiftSchedule week, PayrollPeriod period) {
        LocalDate from = week.getWeekStart().isBefore(period.getStartDate()) ? period.getStartDate() : week.getWeekStart();
        LocalDate lastDay = WeekCalendar.lastDay(week.getWeekStart());
        LocalDate to = lastDay.isAfter(period.getEndDate()) ? period.getEndDate() : lastDay;
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
