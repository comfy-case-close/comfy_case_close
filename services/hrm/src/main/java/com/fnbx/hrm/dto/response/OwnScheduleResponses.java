package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** What an employee sees of an approved schedule: the whole branch by day and period, and their own shifts in lines. */
public final class OwnScheduleResponses {

    private OwnScheduleResponses() {}

    public record MyShift(UUID shiftAssignmentId, LocalDate date, UUID branchId, String branchName, String slotName,
                          LocalTime startTime, LocalTime endTime, String positionName) {}

    public record Person(UUID staffId, String nickname, String positionName, boolean mine) {}

    public record DayView(LocalDate date, List<Person> morning, List<Person> evening) {}

    /** Whether the signed-in person has a contract that is in force or still to start; only then do the personal screens apply. */
    public record Employment(boolean employed) {}

    public record Week(LocalDate weekStart, UUID branchId, String branchName, boolean published, List<DayView> days,
                       List<MyShift> myShifts) {}
}
