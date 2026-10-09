package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.BranchSchedulingSetting;
import com.fnbx.hrm.entity.StaffSchedulingProfile;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.BranchSchedulingSettingRepository;
import com.fnbx.hrm.repository.StaffSchedulingProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Weekly minimum and maximum hours: the branch default, overridden for a person who has an own setting. */
@Component
@RequiredArgsConstructor
public class SchedulingLimitsProvider {

    private static final int MINUTES_PER_HOUR = 60;

    private final BranchSchedulingSettingRepository settingRepository;
    private final StaffSchedulingProfileRepository profileRepository;

    public SchedulingLimits forBranch(UUID branchId) {
        return settingRepository.findById(branchId).map(this::toLimits).orElse(SchedulingLimits.DEFAULT);
    }

    public Map<UUID, Integer> minMinutes(SchedulingLimits limits, Map<UUID, SchedulingStaff> staff, LocalDate weekStart) {
        Map<UUID, StaffSchedulingProfile> profiles = profileRepository.findAllById(staff.keySet()).stream()
                .collect(Collectors.toMap(StaffSchedulingProfile::getStaffId, Function.identity()));
        Map<UUID, Integer> minutes = new HashMap<>();
        staff.values().forEach(person -> minutes.put(person.staffId(), toMinutes(minimumHours(limits, person, profiles, weekStart))));
        return minutes;
    }

    public Map<UUID, BigDecimal> maxHours(SchedulingLimits limits, Collection<UUID> staffIds) {
        Map<UUID, BigDecimal> maximums = new HashMap<>();
        profileRepository.findAllById(staffIds).stream()
                .filter(profile -> profile.getMaxHoursWeek() != null)
                .forEach(profile -> maximums.put(profile.getStaffId(), profile.getMaxHoursWeek()));
        staffIds.stream().filter(id -> !maximums.containsKey(id) && limits.maxHoursWeek() != null)
                .forEach(id -> maximums.put(id, limits.maxHoursWeek()));
        return maximums;
    }

    private BigDecimal minimumHours(SchedulingLimits limits, SchedulingStaff person,
            Map<UUID, StaffSchedulingProfile> profiles, LocalDate weekStart) {
        StaffSchedulingProfile profile = profiles.get(person.staffId());
        if (profile != null && profile.getMinHoursWeek() != null) {
            return profile.getMinHoursWeek();
        }
        EmploymentType type = person.typeOn(weekStart).orElse(EmploymentType.PARTTIME);
        return type == EmploymentType.FULLTIME ? limits.minHoursFullTime() : limits.minHoursPartTime();
    }

    private int toMinutes(BigDecimal hours) {
        return hours.multiply(BigDecimal.valueOf(MINUTES_PER_HOUR)).intValue();
    }

    private SchedulingLimits toLimits(BranchSchedulingSetting setting) {
        return new SchedulingLimits(setting.getMinRestHours(), setting.getMinHoursPartTime(), setting.getMinHoursFullTime(),
                setting.getMaxHoursWeek());
    }
}
