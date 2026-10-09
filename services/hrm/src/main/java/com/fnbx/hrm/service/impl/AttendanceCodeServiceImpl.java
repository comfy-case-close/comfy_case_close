package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.AttendanceCodeRequest;
import com.fnbx.hrm.dto.response.AttendanceCodeResponse;
import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.AttendanceCodeRepository;
import com.fnbx.hrm.service.AttendanceCodeService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceCodeServiceImpl implements AttendanceCodeService {

    private final AttendanceCodeRepository attendanceCodeRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceCodeResponse> list() {
        return attendanceCodeRepository.findAllByOrderByCode().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AttendanceCodeResponse create(AttendanceCodeRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        AttendanceCode code = new AttendanceCode();
        code.setAttendanceCodeId(UUID.randomUUID());
        code.setBusinessId(TenantContext.current().businessId());
        apply(code, request);
        return mapper.toResponse(attendanceCodeRepository.save(code));
    }

    @Override
    @Transactional
    public AttendanceCodeResponse update(UUID attendanceCodeId, AttendanceCodeRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        AttendanceCode code = attendanceCodeRepository.findById(attendanceCodeId)
                .orElseThrow(PayrollExceptions::resourceNotFound);
        apply(code, request);
        return mapper.toResponse(attendanceCodeRepository.save(code));
    }

    private void apply(AttendanceCode code, AttendanceCodeRequest request) {
        code.setCode(request.getCode());
        code.setDescription(request.getDescription());
        code.setDayCredit(request.getDayCredit());
        code.setPaid(request.isPaid());
        code.setConsumesAnnualLeave(request.isConsumesAnnualLeave());
        code.setCountsAsAbsence(request.isCountsAsAbsence());
        code.setFulltimeOnly(request.isFulltimeOnly());
    }
}
