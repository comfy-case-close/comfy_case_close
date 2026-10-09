package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.BulkTimesheetUpdateRequest;
import com.fnbx.hrm.dto.request.AttendanceInputPreviewRequest;
import com.fnbx.hrm.dto.response.BulkTimesheetUpdateResponse;
import com.fnbx.hrm.dto.response.AttendanceInputPreviewResponse;
import com.fnbx.hrm.dto.response.TimesheetCellResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import com.fnbx.hrm.enums.EmploymentType;
import java.time.LocalDate;
import java.util.UUID;

public interface TimesheetService {

    TimesheetGridResponse getGrid(UUID periodId, EmploymentType employmentType, UUID branchId);

    BulkTimesheetUpdateResponse saveCells(UUID periodId, BulkTimesheetUpdateRequest request);

    TimesheetCellResponse saveCell(UUID lineId, LocalDate date, String rawValue, long expectedVersion, String adjustReason);

    void deleteCell(UUID lineId, LocalDate date, String reason);

    AttendanceInputPreviewResponse previewAttendanceInput(AttendanceInputPreviewRequest request);
}
