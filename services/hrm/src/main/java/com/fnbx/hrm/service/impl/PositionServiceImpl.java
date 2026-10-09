package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.PositionProfileRequest;
import com.fnbx.hrm.dto.response.PositionResponse;
import com.fnbx.hrm.entity.Department;
import com.fnbx.hrm.entity.PositionProfile;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.DepartmentRepository;
import com.fnbx.hrm.repository.PositionProfileRepository;
import com.fnbx.hrm.service.PositionService;
import com.fnbx.identity.entity.StaffPosition;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Positions live in identity; payroll only attaches a department and a trainee flag to them. */
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionProfileRepository positionProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<PositionResponse> list() {
        access.requireAnyBusiness(Permission.HR_RECORD_READ, Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return positionProfileRepository.findAllWithProfile();
    }

    @Override
    @Transactional
    public PositionResponse setProfile(UUID positionId, PositionProfileRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        StaffPosition position = entityManager.find(StaffPosition.class, positionId);
        if (position == null) {
            throw PayrollExceptions.resourceNotFound();
        }
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(PayrollExceptions::resourceNotFound);

        PositionProfile profile = positionProfileRepository.findById(positionId).orElseGet(() -> {
            PositionProfile created = new PositionProfile();
            created.setPositionId(positionId);
            created.setBusinessId(TenantContext.current().businessId());
            return created;
        });
        profile.setDepartmentId(department.getDepartmentId());
        profile.setTrainee(request.isTrainee());
        positionProfileRepository.save(profile);

        return PositionResponse.builder()
                .positionId(position.getPositionId())
                .positionCode(position.getPositionCode())
                .positionName(position.getPositionName())
                .active(position.isActive())
                .departmentId(department.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .trainee(profile.isTrainee())
                .build();
    }
}
