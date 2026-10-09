package com.fnbx.hrm.service.timesheet;

import com.fnbx.hrm.enums.TimesheetSource;
import java.util.UUID;

/** Where a timesheet cell comes from; an attendance cell carries its sheet and the number of late shifts of the day. */
public record CellOrigin(TimesheetSource source, UUID attendanceSheetId, int lateShifts, String adjustReason) {

    public static CellOrigin manual(String adjustReason) {
        return new CellOrigin(TimesheetSource.MANUAL, null, 0, adjustReason);
    }

    public static CellOrigin attendance(UUID attendanceSheetId, int lateShifts) {
        return new CellOrigin(TimesheetSource.ATTENDANCE, attendanceSheetId, lateShifts, null);
    }
}
