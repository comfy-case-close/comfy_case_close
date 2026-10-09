package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.OwnScheduleResponses;
import java.time.LocalDate;
import java.util.List;

/** The signed-in employee's view of approved schedules; nothing is shown before the general manager approves. */
public interface OwnScheduleService {

    List<OwnScheduleResponses.MyShift> myShifts(LocalDate from, LocalDate to);

    OwnScheduleResponses.Week week(LocalDate weekStart);

    OwnScheduleResponses.Employment employment();
}
