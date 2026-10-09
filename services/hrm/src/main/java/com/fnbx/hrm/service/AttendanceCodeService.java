package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.AttendanceCodeRequest;
import com.fnbx.hrm.dto.response.AttendanceCodeResponse;
import java.util.List;
import java.util.UUID;

public interface AttendanceCodeService {

    List<AttendanceCodeResponse> list();

    AttendanceCodeResponse create(AttendanceCodeRequest request);

    AttendanceCodeResponse update(UUID attendanceCodeId, AttendanceCodeRequest request);
}
