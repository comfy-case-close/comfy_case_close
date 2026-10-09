package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.enums.ShiftDayType;
import com.fnbx.hrm.exception.PayrollExceptions;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class WeekCalendar {

    private static final int DAYS_IN_WEEK = 7;

    private WeekCalendar() {}

    public static LocalDate requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw PayrollExceptions.invalidField("weekStart must be a Monday");
        }
        return weekStart;
    }

    public static List<LocalDate> days(LocalDate weekStart) {
        return weekStart.datesUntil(weekStart.plusDays(DAYS_IN_WEEK)).toList();
    }

    public static LocalDate lastDay(LocalDate weekStart) {
        return weekStart.plusDays(DAYS_IN_WEEK - 1);
    }

    public static ShiftDayType dayTypeOf(LocalDate date) {
        return date.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue() ? ShiftDayType.WEEKEND : ShiftDayType.WEEKDAY;
    }

    public static boolean hasEnded(LocalDate weekStart, LocalDate today) {
        return today.isAfter(lastDay(weekStart));
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
