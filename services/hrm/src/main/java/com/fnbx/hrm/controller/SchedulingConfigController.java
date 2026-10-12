package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.SchedulingConfigRequests;
import com.fnbx.hrm.dto.response.SchedulingConfigResponses;
import com.fnbx.hrm.service.SchedulingConfigService;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shift slots, minimum staffing and weekly-hours defaults. */
@RestController
@RequiredArgsConstructor
public class SchedulingConfigController {

    private final SchedulingConfigService configService;

    @GetMapping("/shift-slots")
    public ResponseEntity<List<SchedulingConfigResponses.ShiftSlot>> slots(@RequestParam UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(configService.slots(branchId, asOf));
    }

    @PostMapping("/shift-slots")
    public ResponseEntity<SchedulingConfigResponses.ShiftSlot> createSlot(@Valid @RequestBody SchedulingConfigRequests.ShiftSlot request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createSlot(request));
    }

    @GetMapping("/shift-period-requirements")
    public ResponseEntity<List<SchedulingConfigResponses.Requirement>> requirements(@RequestParam UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(configService.requirements(branchId, asOf));
    }

    @PutMapping("/shift-period-requirements")
    public ResponseEntity<List<SchedulingConfigResponses.Requirement>> replaceRequirements(
            @Valid @RequestBody SchedulingConfigRequests.Requirements request) {
        return ResponseEntity.ok(configService.replaceRequirements(request));
    }

    @GetMapping("/branches/{branchId}/scheduling-settings")
    public ResponseEntity<SchedulingConfigResponses.BranchSetting> branchSetting(@PathVariable UUID branchId) {
        return ResponseEntity.ok(configService.branchSetting(branchId));
    }

    @PutMapping("/branches/{branchId}/scheduling-settings")
    public ResponseEntity<SchedulingConfigResponses.BranchSetting> saveBranchSetting(@PathVariable UUID branchId,
            @Valid @RequestBody SchedulingConfigRequests.BranchSetting request) {
        return ResponseEntity.ok(configService.saveBranchSetting(branchId, request));
    }

    @GetMapping("/staff/{staffId}/scheduling-profile")
    public ResponseEntity<SchedulingConfigResponses.StaffProfile> staffProfile(@PathVariable UUID staffId) {
        return ResponseEntity.ok(configService.staffProfile(staffId));
    }

    @PutMapping("/staff/{staffId}/scheduling-profile")
    public ResponseEntity<SchedulingConfigResponses.StaffProfile> saveStaffProfile(@PathVariable UUID staffId,
            @RequestBody SchedulingConfigRequests.StaffProfile request) {
        return ResponseEntity.ok(configService.saveStaffProfile(staffId, request));
    }

    @GetMapping("/own/scheduling-profile")
    public ResponseEntity<SchedulingConfigResponses.StaffProfile> ownProfile() {
        return ResponseEntity.ok(configService.staffProfile(TenantContext.current().userId()));
    }
}
