package com.fnbx.hrm.service;

import com.fnbx.hrm.entity.BranchSetting;
import com.fnbx.hrm.repository.BranchSettingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A line may be rostered at its assignment's own default branch, or - when that
 * default branch is the shared-pool bucket ("Da chi nhanh") - at any branch.
 * Spec section 8.13 (E88).
 */
@Component
@RequiredArgsConstructor
public class PayrollLineBranchPolicy {

    private final BranchSettingRepository branchSettingRepository;

    public boolean isAllowed(UUID assignmentDefaultBranchId, UUID targetBranchId) {
        if (assignmentDefaultBranchId.equals(targetBranchId)) {
            return true;
        }
        return branchSettingRepository.findById(assignmentDefaultBranchId)
                .map(BranchSetting::isSharedPool)
                .orElse(false);
    }
}
