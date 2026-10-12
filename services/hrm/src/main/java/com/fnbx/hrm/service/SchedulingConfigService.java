package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.SchedulingConfigRequests;
import com.fnbx.hrm.dto.response.SchedulingConfigResponses;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchedulingConfigService {

    List<SchedulingConfigResponses.ShiftSlot> slots(UUID branchId, LocalDate asOf);

    SchedulingConfigResponses.ShiftSlot createSlot(SchedulingConfigRequests.ShiftSlot request);

    List<SchedulingConfigResponses.Requirement> requirements(UUID branchId, LocalDate asOf);

    List<SchedulingConfigResponses.Requirement> replaceRequirements(SchedulingConfigRequests.Requirements request);

    SchedulingConfigResponses.BranchSetting branchSetting(UUID branchId);

    SchedulingConfigResponses.BranchSetting saveBranchSetting(UUID branchId, SchedulingConfigRequests.BranchSetting request);

    SchedulingConfigResponses.StaffProfile staffProfile(UUID staffId);

    java.util.List<SchedulingConfigResponses.StaffProfile> staffProfiles();

    SchedulingConfigResponses.StaffProfile saveStaffProfile(UUID staffId, SchedulingConfigRequests.StaffProfile request);
}
