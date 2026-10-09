package com.fnbx.hrm.service.timesheet;

import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.LatePenaltyRule;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CellLookups(BigDecimal standardHoursPerDay, Map<String, AttendanceCode> attendanceCodesByCode,
                          Map<String, LatePenaltyRule> lateRulesByCode, Set<UUID> absenceCodeIds) {
}
