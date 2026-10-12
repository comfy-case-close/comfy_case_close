package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.service.contract.EmploymentAssignmentWriter;
import com.fnbx.hrm.service.employee.EmployeeProfileApplier;
import com.fnbx.shared.tenant.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractRowCommitter {

    private final EmployeeProfileRepository profileRepository;
    private final ProfileFieldMerger profileMerger;
    private final EmployeeProfileApplier profileApplier;
    private final EmploymentAssignmentWriter assignmentWriter;

    /** Writes the profile (blanks only) and the contract of a row that passed validation; returns the new contract id. */
    public UUID commit(ValidatedRow row) {
        EmployeeProfile profile = profileRepository.findById(row.staffId()).orElseGet(() -> newProfile(row.staffId()));
        profileApplier.apply(profile, profileMerger.merge(profile, row.profile()).blanksOnly());
        profileRepository.saveAndFlush(profile);
        return assignmentWriter.create(row.staffId(), row.assignment()).getEmploymentAssignmentId();
    }

    private EmployeeProfile newProfile(UUID staffId) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setStaffId(staffId);
        profile.setBusinessId(TenantContext.current().businessId());
        return profile;
    }
}
