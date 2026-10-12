package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.SchedulingConfigRequests;
import com.fnbx.hrm.dto.response.SchedulingConfigResponses;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Slots, minimum staffing per shift period, and the weekly-hours defaults of a branch and of a person. */
public interface SchedulingConfigService {

    List<SchedulingConfigResponses.ShiftSlot> slots(UUID branchId, LocalDate asOf);

    SchedulingConfigResponses.ShiftSlot createSlot(SchedulingConfigRequests.ShiftSlot request);

    List<SchedulingConfigResponses.Requirement> requirements(UUID branchId, LocalDate asOf);

    List<SchedulingConfigResponses.Requirement> replaceRequirements(SchedulingConfigRequests.Requirements request);

    SchedulingConfigResponses.BranchSetting branchSetting(UUID branchId);

    SchedulingConfigResponses.BranchSetting saveBranchSetting(UUID branchId, SchedulingConfigRequests.BranchSetting request);

    SchedulingConfigResponses.StaffProfile staffProfile(UUID staffId);

    SchedulingConfigResponses.StaffProfile saveStaffProfile(UUID staffId, SchedulingConfigRequests.StaffProfile request);
}
