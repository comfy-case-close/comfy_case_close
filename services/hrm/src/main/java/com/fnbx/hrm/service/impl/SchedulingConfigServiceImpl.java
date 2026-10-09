package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.SchedulingConfigRequests;
import com.fnbx.hrm.dto.response.SchedulingConfigResponses;
import com.fnbx.hrm.entity.BranchSchedulingSetting;
import com.fnbx.hrm.entity.ShiftPeriodRequirement;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.entity.StaffSchedulingProfile;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.BranchSchedulingSettingRepository;
import com.fnbx.hrm.repository.ShiftPeriodRequirementRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.repository.StaffSchedulingProfileRepository;
import com.fnbx.hrm.service.SchedulingConfigService;
import com.fnbx.hrm.service.scheduling.SchedulingLimits;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchedulingConfigServiceImpl implements SchedulingConfigService {

    private final ShiftSlotRepository slotRepository;
    private final ShiftPeriodRequirementRepository requirementRepository;
    private final BranchSchedulingSettingRepository settingRepository;
    private final StaffSchedulingProfileRepository profileRepository;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public List<SchedulingConfigResponses.ShiftSlot> slots(UUID branchId, LocalDate asOf) {
        branchAccess.require(branchId, Permission.SHIFT_SCHEDULE_READ);
        return slotRepository.findAllEffective(branchId, asOf == null ? LocalDate.now() : asOf).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SchedulingConfigResponses.ShiftSlot createSlot(SchedulingConfigRequests.ShiftSlot request) {
        branchAccess.requireBusiness(Permission.SHIFT_CONFIG_WRITE);
        if (!request.endTime().isAfter(request.startTime())) {
            throw PayrollExceptions.invalidField("endTime must be after startTime");
        }
        slotRepository.findCurrentVersions(request.branchId(), request.name(), request.dayType(), request.effectiveFrom())
                .forEach(current -> current.setEffectiveTo(request.effectiveFrom().minusDays(1)));
        ShiftSlot slot = new ShiftSlot();
        slot.setShiftSlotId(UUID.randomUUID());
        slot.setBusinessId(TenantContext.current().businessId());
        slot.setBranchId(request.branchId());
        slot.setName(request.name());
        slot.setShiftPeriod(request.period());
        slot.setEmploymentType(request.employmentType());
        slot.setDayType(request.dayType());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setEffectiveFrom(request.effectiveFrom());
        slot.setSortOrder(request.sortOrder());
        slot.setActive(true);
        try {
            return toResponse(slotRepository.saveAndFlush(slot));
        } catch (DataIntegrityViolationException ex) {
            throw PayrollExceptions.resourceConflict("A newer version of this shift slot already exists");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchedulingConfigResponses.Requirement> requirements(UUID branchId, LocalDate asOf) {
        branchAccess.require(branchId, Permission.SHIFT_SCHEDULE_READ);
        return requirementRepository.findInForce(branchId, asOf == null ? LocalDate.now() : asOf).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public List<SchedulingConfigResponses.Requirement> replaceRequirements(SchedulingConfigRequests.Requirements request) {
        branchAccess.requireBusiness(Permission.SHIFT_CONFIG_WRITE);
        requirementRepository.deleteByBranchIdAndEffectiveFrom(request.branchId(), request.effectiveFrom());
        requirementRepository.flush();
        List<ShiftPeriodRequirement> rows = request.items().stream().map(item -> {
            ShiftPeriodRequirement row = new ShiftPeriodRequirement();
            row.setShiftPeriodRequirementId(UUID.randomUUID());
            row.setBusinessId(TenantContext.current().businessId());
            row.setBranchId(request.branchId());
            row.setShiftPeriod(item.period());
            row.setPositionId(item.positionId());
            row.setMinStaff(item.minStaff());
            row.setEffectiveFrom(request.effectiveFrom());
            return row;
        }).toList();
        return requirementRepository.saveAll(rows).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SchedulingConfigResponses.BranchSetting branchSetting(UUID branchId) {
        branchAccess.require(branchId, Permission.SHIFT_SCHEDULE_READ);
        return settingRepository.findById(branchId).map(this::toResponse).orElseGet(() -> defaults(branchId));
    }

    @Override
    @Transactional
    public SchedulingConfigResponses.BranchSetting saveBranchSetting(UUID branchId, SchedulingConfigRequests.BranchSetting request) {
        branchAccess.requireBusiness(Permission.SHIFT_CONFIG_WRITE);
        BranchSchedulingSetting setting = settingRepository.findById(branchId).orElseGet(() -> {
            BranchSchedulingSetting created = new BranchSchedulingSetting();
            created.setBranchId(branchId);
            created.setBusinessId(TenantContext.current().businessId());
            return created;
        });
        setting.setMinRestHours(request.minRestHours());
        setting.setMinHoursPartTime(request.minHoursPartTime());
        setting.setMinHoursFullTime(request.minHoursFullTime());
        setting.setMaxHoursWeek(request.maxHoursWeek());
        return toResponse(settingRepository.saveAndFlush(setting));
    }

    @Override
    @Transactional(readOnly = true)
    public SchedulingConfigResponses.StaffProfile staffProfile(UUID staffId) {
        requireConfigWriterOrSelf(staffId);
        return profileRepository.findById(staffId).map(this::toResponse)
                .orElseGet(() -> new SchedulingConfigResponses.StaffProfile(staffId, null, null));
    }

    @Override
    @Transactional
    public SchedulingConfigResponses.StaffProfile saveStaffProfile(UUID staffId, SchedulingConfigRequests.StaffProfile request) {
        branchAccess.requireBusiness(Permission.SHIFT_CONFIG_WRITE);
        StaffSchedulingProfile profile = profileRepository.findById(staffId).orElseGet(() -> {
            StaffSchedulingProfile created = new StaffSchedulingProfile();
            created.setStaffId(staffId);
            created.setBusinessId(TenantContext.current().businessId());
            return created;
        });
        profile.setMinHoursWeek(request.minHoursWeek());
        profile.setMaxHoursWeek(request.maxHoursWeek());
        return toResponse(profileRepository.saveAndFlush(profile));
    }

    private void requireConfigWriterOrSelf(UUID staffId) {
        if (!staffId.equals(TenantContext.current().userId())) {
            branchAccess.requireBusiness(Permission.SHIFT_CONFIG_WRITE);
        }
    }

    private SchedulingConfigResponses.BranchSetting defaults(UUID branchId) {
        SchedulingLimits limits = SchedulingLimits.DEFAULT;
        return new SchedulingConfigResponses.BranchSetting(branchId, (short) limits.minRestHours(), limits.minHoursPartTime(),
                limits.minHoursFullTime(), limits.maxHoursWeek());
    }

    private SchedulingConfigResponses.ShiftSlot toResponse(ShiftSlot slot) {
        return new SchedulingConfigResponses.ShiftSlot(slot.getShiftSlotId(), slot.getBranchId(), slot.getName(),
                slot.getShiftPeriod().name(), slot.getEmploymentType().name(), slot.getDayType().name(), slot.getStartTime(),
                slot.getEndTime(), slot.getEffectiveFrom(), slot.getEffectiveTo(), slot.getSortOrder(), slot.isActive());
    }

    private SchedulingConfigResponses.Requirement toResponse(ShiftPeriodRequirement row) {
        return new SchedulingConfigResponses.Requirement(row.getShiftPeriod().name(), row.getPositionId(), row.getMinStaff(),
                row.getEffectiveFrom());
    }

    private SchedulingConfigResponses.BranchSetting toResponse(BranchSchedulingSetting setting) {
        return new SchedulingConfigResponses.BranchSetting(setting.getBranchId(), setting.getMinRestHours(),
                setting.getMinHoursPartTime(), setting.getMinHoursFullTime(), setting.getMaxHoursWeek());
    }

    private SchedulingConfigResponses.StaffProfile toResponse(StaffSchedulingProfile profile) {
        return new SchedulingConfigResponses.StaffProfile(profile.getStaffId(), profile.getMinHoursWeek(), profile.getMaxHoursWeek());
    }
}
