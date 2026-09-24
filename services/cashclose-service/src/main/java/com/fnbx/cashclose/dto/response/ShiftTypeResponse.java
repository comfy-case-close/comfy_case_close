package com.fnbx.cashclose.dto.response;

import java.time.LocalTime;
import java.util.UUID;

public record ShiftTypeResponse(UUID shiftTypeId, String shiftCode, String shiftName,
                                int sortOrder, LocalTime suggestedStartTime,
                                LocalTime suggestedEndTime, LocalTime submitDeadline) {}
