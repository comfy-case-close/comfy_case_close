package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.SaveAvailabilityRequest;
import com.fnbx.hrm.dto.response.AvailabilityResponses;
import java.time.LocalDate;
import java.util.UUID;

/** Weekly registration: the store manager opens and closes it, employees mark the shifts they are busy. */
public interface AvailabilityService {

    AvailabilityResponses.Window openWindow(UUID branchId, LocalDate weekStart);

    AvailabilityResponses.Window closeWindow(UUID branchId, LocalDate weekStart);

    AvailabilityResponses.OwnForm own(LocalDate weekStart);

    AvailabilityResponses.OwnForm saveOwn(LocalDate weekStart, SaveAvailabilityRequest request);

    AvailabilityResponses.OwnForm submitOwn(LocalDate weekStart);

    AvailabilityResponses.OwnForm copyOwn(LocalDate weekStart, LocalDate fromWeek);

    AvailabilityResponses.Overview overview(UUID branchId, LocalDate weekStart, String search);

    /** The store manager enters the registration for an employee; it is stored as submitted. */
    AvailabilityResponses.OwnForm enterFor(UUID staffId, LocalDate weekStart, SaveAvailabilityRequest request);

    /** Mails every employee who has not sent the registration; returns how many were reminded. */
    int remind(UUID branchId, LocalDate weekStart);
}
